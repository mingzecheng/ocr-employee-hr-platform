package com.hrplatform.inventory;

import com.hrplatform.common.security.DataScope;
import com.hrplatform.archive.ArchiveMapper;
import com.hrplatform.archive.ArchiveOcrSource;
import com.hrplatform.archive.ArchiveVersion;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryServiceTest {
    @Test
    void startingTaskUsesAnAtomicDraftTransition() {
        InventoryMapper mapper = Mockito.mock(InventoryMapper.class);
        InventoryTask task = new InventoryTask(5L, "INV-1", "DRAFT", 9L);
        Mockito.when(mapper.findTask(5L, null, null, "ALL")).thenReturn(task);
        Mockito.when(mapper.transitionTask(5L, "DRAFT", "IN_PROGRESS")).thenReturn(1);

        InventoryTask started = new InventoryService(mapper).start(5L,
                new InventoryActor(9L, new DataScope(DataScope.Type.ALL, 9L, null, null)));

        org.assertj.core.api.Assertions.assertThat(started.status()).isEqualTo("IN_PROGRESS");
        Mockito.verify(mapper).transitionTask(5L, "DRAFT", "IN_PROGRESS");
    }

    @Test
    void departmentManagerCannotStartATaskContainingAnotherDepartment() {
        InventoryMapper mapper = Mockito.mock(InventoryMapper.class);
        InventoryTask task = new InventoryTask(5L, "INV-1", "DRAFT", 9L);
        Mockito.when(mapper.findTask(5L, 3L, null, "DEPARTMENT")).thenReturn(task);
        Mockito.when(mapper.listAllItems(5L)).thenReturn(java.util.List.of(
                new InventoryItem(12L, 5L, 30L, 1, null, 7L, 3L, null, null, null, null, "PENDING", null),
                new InventoryItem(13L, 5L, 31L, 1, null, 8L, 4L, null, null, null, null, "PENDING", null)));

        assertThatThrownBy(() -> new InventoryService(mapper).start(5L,
                new InventoryActor(9L, new DataScope(DataScope.Type.DEPARTMENT, 9L, null, 3L))))
                .isInstanceOf(InventoryStateException.class)
                .hasMessageContaining("完整范围");

        Mockito.verify(mapper, Mockito.never()).transitionTask(5L, "DRAFT", "IN_PROGRESS");
    }

    @Test
    void departmentManagerCannotCompleteATaskContainingAnotherDepartment() {
        InventoryMapper mapper = Mockito.mock(InventoryMapper.class);
        InventoryTask task = new InventoryTask(5L, "INV-1", "IN_PROGRESS", 9L);
        Mockito.when(mapper.findTask(5L, 3L, null, "DEPARTMENT")).thenReturn(task);
        Mockito.when(mapper.listAllItems(5L)).thenReturn(java.util.List.of(
                new InventoryItem(12L, 5L, 30L, 1, 30L, 7L, 3L,
                        "PRESENT", "NONE", null, 9L, "NOT_REQUIRED", null),
                new InventoryItem(13L, 5L, 31L, 1, 31L, 8L, 4L,
                        "PRESENT", "NONE", null, 9L, "NOT_REQUIRED", null)));

        assertThatThrownBy(() -> new InventoryService(mapper).complete(5L,
                new InventoryActor(9L, new DataScope(DataScope.Type.DEPARTMENT, 9L, null, 3L))))
                .isInstanceOf(InventoryStateException.class)
                .hasMessageContaining("完整范围");

        Mockito.verify(mapper, Mockito.never()).transitionTask(5L, "IN_PROGRESS", "COMPLETED");
    }

    @Test
    void completingCheckedItemsUsesAnAtomicInProgressTransition() {
        InventoryMapper mapper = Mockito.mock(InventoryMapper.class);
        InventoryTask task = new InventoryTask(5L, "INV-1", "IN_PROGRESS", 9L);
        InventoryItem item = new InventoryItem(12L, 5L, 30L, 1, 30L, 7L, 3L,
                "PRESENT", "NONE", "现场版本与档案版本一致", 9L, "NOT_REQUIRED", null);
        Mockito.when(mapper.transitionTask(5L, "IN_PROGRESS", "COMPLETED")).thenReturn(1);

        InventoryTask completed = new InventoryService(mapper).complete(task, java.util.List.of(item));

        org.assertj.core.api.Assertions.assertThat(completed.status()).isEqualTo("COMPLETED");
        Mockito.verify(mapper).transitionTask(5L, "IN_PROGRESS", "COMPLETED");
    }

    @Test
    void checkingMissingArchiveCreatesAnOpenException() {
        InventoryMapper mapper = Mockito.mock(InventoryMapper.class);
        ArchiveMapper archiveMapper = Mockito.mock(ArchiveMapper.class);
        InventoryTask task = new InventoryTask(5L, "INV-1", "IN_PROGRESS", 9L);
        InventoryItem item = new InventoryItem(12L, 5L, 30L, 1, null, 7L, 3L,
                null, null, null, null, "PENDING", null);
        Mockito.when(mapper.findTask(5L, null, null, "ALL")).thenReturn(task);
        Mockito.when(mapper.findItem(5L, 12L, null, null, "ALL")).thenReturn(item);
        Mockito.when(mapper.recordCheck(Mockito.any())).thenReturn(1);
        Mockito.when(mapper.insertExceptionRecord(12L, "MISSING", "原件未找到")).thenReturn(1);

        InventoryItem checked = new InventoryService(mapper, archiveMapper).checkItem(5L, 12L,
                new InventoryCheckRequest("MISSING", null, "原件未找到"),
                new InventoryActor(9L, new DataScope(DataScope.Type.ALL, 9L, null, null)));

        org.assertj.core.api.Assertions.assertThat(checked.differenceType()).isEqualTo("MISSING");
        org.assertj.core.api.Assertions.assertThat(checked.resolutionStatus()).isEqualTo("OPEN");
        Mockito.verify(mapper).insertExceptionRecord(12L, "MISSING", "原件未找到");
    }

    @Test
    void checkingVersionFromAnotherArchiveDocumentIsRejected() {
        InventoryMapper mapper = Mockito.mock(InventoryMapper.class);
        ArchiveMapper archiveMapper = Mockito.mock(ArchiveMapper.class);
        InventoryTask task = new InventoryTask(5L, "INV-1", "IN_PROGRESS", 9L);
        InventoryItem item = new InventoryItem(12L, 5L, 30L, 1, null, 7L, 3L,
                null, null, null, null, "PENDING", null);
        Mockito.when(mapper.findTask(5L, null, null, "ALL")).thenReturn(task);
        Mockito.when(mapper.findItem(5L, 12L, null, null, "ALL")).thenReturn(item);
        Mockito.when(mapper.recordCheck(Mockito.any())).thenReturn(1);
        Mockito.when(mapper.insertExceptionRecord(12L, "VERSION_MISMATCH", "现场版本与档案版本不一致"))
                .thenReturn(1);
        Mockito.when(archiveMapper.findOcrSourceByVersionIdWithScope(31L, null, null, "ALL"))
                .thenReturn(new ArchiveOcrSource(31L, "key", "other.pdf", "image/png", 10L, "hash",
                        "profile", 8L, 3L));
        Mockito.when(archiveMapper.findVersionByIdWithScope(30L, null, null, "ALL"))
                .thenReturn(version(30L, 100L));
        Mockito.when(archiveMapper.findVersionByIdWithScope(31L, null, null, "ALL"))
                .thenReturn(version(31L, 200L));

        InventoryService service = new InventoryService(mapper, archiveMapper);

        assertThatThrownBy(() -> service.checkItem(5L, 12L,
                new InventoryCheckRequest("PRESENT", 31L, null),
                new InventoryActor(9L, new DataScope(DataScope.Type.ALL, 9L, null, null))))
                .isInstanceOf(InventoryStateException.class)
                .hasMessageContaining("同一份档案材料");
        Mockito.verify(mapper, Mockito.never()).recordCheck(Mockito.any());
    }

    @Test
    void availableVersionsAreLimitedToTheInventoryItemsArchiveDocument() {
        InventoryMapper mapper = Mockito.mock(InventoryMapper.class);
        ArchiveMapper archiveMapper = Mockito.mock(ArchiveMapper.class);
        InventoryTask task = new InventoryTask(5L, "INV-1", "IN_PROGRESS", 9L);
        InventoryItem item = new InventoryItem(12L, 5L, 30L, 1, null, 7L, 3L,
                null, null, null, null, "PENDING", null);
        Mockito.when(mapper.findTask(5L, null, null, "ALL")).thenReturn(task);
        Mockito.when(mapper.findItem(5L, 12L, null, null, "ALL")).thenReturn(item);
        Mockito.when(archiveMapper.findVersionByIdWithScope(30L, null, null, "ALL"))
                .thenReturn(version(30L, 100L));
        Mockito.when(archiveMapper.listVersions(100L, null, null, "ALL", 0, 100))
                .thenReturn(java.util.List.of(version(31L, 100L), version(30L, 100L)));

        var options = new InventoryService(mapper, archiveMapper).availableVersions(5L, 12L,
                new InventoryActor(9L, new DataScope(DataScope.Type.ALL, 9L, null, null)));

        org.assertj.core.api.Assertions.assertThat(options)
                .extracting(option -> option.id()).containsExactly(31L, 30L);
    }

    @Test
    void resolvingAnExceptionPersistsTheResolutionBeforeTaskCanComplete() {
        InventoryMapper mapper = Mockito.mock(InventoryMapper.class);
        ArchiveMapper archiveMapper = Mockito.mock(ArchiveMapper.class);
        InventoryTask task = new InventoryTask(5L, "INV-1", "IN_PROGRESS", 9L);
        InventoryItem item = new InventoryItem(12L, 5L, 30L, 1, null, 7L, 3L,
                "MISSING", "MISSING", "原件未找到", 9L, "OPEN", null);
        Mockito.when(mapper.findTask(5L, null, null, "ALL")).thenReturn(task);
        Mockito.when(mapper.findItem(5L, 12L, null, null, "ALL")).thenReturn(item);
        Mockito.when(mapper.resolveExceptionRecord(12L, 9L, "已完成补件")).thenReturn(1);
        Mockito.when(mapper.updateResolutionStatus(12L, "RESOLVED")).thenReturn(1);

        InventoryItem resolved = new InventoryService(mapper, archiveMapper).resolve(5L, 12L,
                "已完成补件", new InventoryActor(9L,
                        new DataScope(DataScope.Type.ALL, 9L, null, null)));

        org.assertj.core.api.Assertions.assertThat(resolved.resolutionStatus()).isEqualTo("RESOLVED");
        Mockito.verify(mapper).resolveExceptionRecord(12L, 9L, "已完成补件");
    }

    @Test
    void itemListRequiresAnAccessibleTask() {
        InventoryMapper mapper = Mockito.mock(InventoryMapper.class);
        InventoryTask task = new InventoryTask(5L, "INV-1", "IN_PROGRESS", 9L);
        InventoryItem item = new InventoryItem(12L, 5L, 30L, 1, null, 7L, 3L,
                null, null, null, null, "PENDING", null);
        Mockito.when(mapper.findTask(5L, 3L, null, "DEPARTMENT")).thenReturn(task);
        Mockito.when(mapper.listItems(5L, 3L, null, "DEPARTMENT")).thenReturn(java.util.List.of(item));

        var items = new InventoryService(mapper).listItems(5L,
                new InventoryActor(9L, new DataScope(DataScope.Type.DEPARTMENT, 9L, null, 3L)));

        org.assertj.core.api.Assertions.assertThat(items).containsExactly(item);
    }

    @Test
    void createReadsGeneratedTaskIdByTaskNumberAndPersistsItems() {
        InventoryMapper mapper = Mockito.mock(InventoryMapper.class);
        ArchiveMapper archiveMapper = Mockito.mock(ArchiveMapper.class);
        InventoryTask saved = new InventoryTask(5L, "INV-1", "DRAFT", 9L);
        Mockito.when(mapper.insertTask(Mockito.any())).thenReturn(1);
        Mockito.when(mapper.findTaskByTaskNo(Mockito.anyString())).thenReturn(saved);
        Mockito.when(archiveMapper.findOcrSourceByVersionIdWithScope(12L, null, null, "ALL"))
                .thenReturn(new ArchiveOcrSource(12L, "key", "file.png", "image/png", 10L, "hash",
                        "profile", 7L, 3L));
        Mockito.when(mapper.insertItem(Mockito.any())).thenReturn(1);

        InventoryTask result = new InventoryService(mapper, archiveMapper).create(
                new InventoryCreateRequest(java.util.List.of(12L)), new InventoryActor(9L,
                        new DataScope(DataScope.Type.ALL, 9L, null, null)));

        org.assertj.core.api.Assertions.assertThat(result.id()).isEqualTo(5L);
        Mockito.verify(mapper).insertItem(Mockito.argThat(item -> item.taskId().equals(5L)));
    }

    @Test
    void createRejectsDuplicateVersionIdsBeforeWritingTask() {
        InventoryMapper mapper = Mockito.mock(InventoryMapper.class);
        ArchiveMapper archiveMapper = Mockito.mock(ArchiveMapper.class);

        assertThatThrownBy(() -> new InventoryService(mapper, archiveMapper).create(
                new InventoryCreateRequest(java.util.List.of(12L, 12L)), new InventoryActor(9L,
                        new DataScope(DataScope.Type.ALL, 9L, null, null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复");

        Mockito.verifyNoInteractions(mapper, archiveMapper);
    }

    @Test
    void createRejectsMoreThanTwoHundredVersionsBeforeWritingTask() {
        InventoryMapper mapper = Mockito.mock(InventoryMapper.class);
        ArchiveMapper archiveMapper = Mockito.mock(ArchiveMapper.class);
        java.util.List<Long> versionIds = java.util.stream.LongStream.rangeClosed(1, 201)
                .boxed().toList();

        assertThatThrownBy(() -> new InventoryService(mapper, archiveMapper).create(
                new InventoryCreateRequest(versionIds), new InventoryActor(9L,
                        new DataScope(DataScope.Type.ALL, 9L, null, null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("200");

        Mockito.verifyNoInteractions(mapper, archiveMapper);
    }

    @Test
    void createRejectsNonPositiveVersionIdsBeforeWritingTask() {
        InventoryMapper mapper = Mockito.mock(InventoryMapper.class);
        ArchiveMapper archiveMapper = Mockito.mock(ArchiveMapper.class);

        assertThatThrownBy(() -> new InventoryService(mapper, archiveMapper).create(
                new InventoryCreateRequest(java.util.List.of(0L)), new InventoryActor(9L,
                        new DataScope(DataScope.Type.ALL, 9L, null, null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("正数");

        Mockito.verifyNoInteractions(mapper, archiveMapper);
    }

    @Test
    void completionIsBlockedWhenAnItemIsUnresolved() {
        InventoryTask task = new InventoryTask(1L, "INV-1", "IN_PROGRESS");
        InventoryItem item = new InventoryItem(2L, 1L, 8L, 1, null, null, null,
                null, null, null, null, "PENDING", null);
        InventoryService service = new InventoryService(null);

        assertThatThrownBy(() -> service.complete(task, java.util.List.of(item)))
                .isInstanceOf(InventoryStateException.class);
    }

    @Test
    void listUsesDepartmentScope() {
        InventoryMapper mapper = Mockito.mock(InventoryMapper.class);
        Mockito.when(mapper.listTasks(8L, null, "DEPARTMENT", 0, 20))
                .thenReturn(java.util.List.of(new InventoryTask(1L, "INV-1", "DRAFT", 9L)));
        Mockito.when(mapper.countTasks(8L, null, "DEPARTMENT")).thenReturn(1L);

        InventoryPage page = new InventoryService(mapper).list(1, 20,
                new InventoryActor(9L, new DataScope(DataScope.Type.DEPARTMENT, 9L, null, 8L)));

        org.assertj.core.api.Assertions.assertThat(page.total()).isEqualTo(1L);
    }

    private ArchiveVersion version(long id, long documentId) {
        return new ArchiveVersion(id, documentId, 1, id, "record.pdf", "application/pdf", 10L,
                "hash", "PUBLISHED", false, null, 9L, null);
    }
}
