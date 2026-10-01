from __future__ import annotations

from datetime import datetime, timezone
from typing import Generic, Literal, TypeVar

from pydantic import BaseModel, ConfigDict, Field


class ApiModel(BaseModel):
    # 内部使用 snake_case，接口通过 alias 对外保持现有的 camelCase 契约。
    model_config = ConfigDict(populate_by_name=True)


ResponseData = TypeVar("ResponseData")


class ApiResponse(ApiModel, Generic[ResponseData]):
    """所有业务接口共用的响应包装，便于前端统一处理成功和失败结果。"""

    code: int | str = Field(description="业务结果码；0 表示成功，字符串表示具体错误类型")
    message: str = Field(description="面向调用方的结果说明")
    data: ResponseData | None = Field(description="业务数据；失败时通常为 null")


class HealthResponse(ApiModel):
    """服务健康检查结果。"""

    status: Literal["ok"] = Field(description="服务状态；返回 ok 表示进程可用")
    engine: str = Field(description="当前启用的 OCR 引擎名称")


class SourceFile(ApiModel):
    original_name: str = Field(alias="originalName", description="用户上传时的原始文件名")
    stored_name: str = Field(alias="storedName", description="服务端保存文件名，使用随机值避免覆盖")
    content_type: str = Field(alias="contentType", description="文件 MIME 类型，例如 image/png")
    size: int = Field(description="原始文件大小，单位为字节")
    sha256: str = Field(description="原始文件 SHA-256，用于完整性校验和去重")


class DetectionPreview(ApiModel):
    """OCR 文本检测框的派生可视化证据。"""

    url: str = Field(description="效果图下载接口地址")
    stored_name: str = Field(alias="storedName", description="服务端保存的效果图文件名")
    content_type: str = Field(alias="contentType", description="效果图 MIME 类型")
    size: int = Field(ge=0, description="效果图文件大小，单位为字节")
    sha256: str = Field(description="效果图 SHA-256，用于完整性校验")
    width: int = Field(ge=1, description="效果图宽度，单位为像素")
    height: int = Field(ge=1, description="效果图高度，单位为像素")


class TextBlock(ApiModel):
    page_no: int = Field(default=1, alias="pageNo", description="文本所在页码，从 1 开始")
    text: str = Field(description="OCR 识别出的原始文本")
    confidence: float = Field(description="文本识别置信度，取值范围 0 到 1")
    bbox: list[int] = Field(description="文本位置框，格式为 [左, 上, 右, 下]")


class FieldResult(ApiModel):
    field_code: str = Field(alias="fieldCode", description="后续字段抽取服务定义的业务字段编码")
    value: str = Field(description="后续字段抽取服务生成的字段值")
    confidence: float = Field(description="字段识别置信度，取值范围 0 到 1")
    bbox: list[int] = Field(description="字段在原图中的位置框，格式为 [左, 上, 右, 下]")
    page_no: int = Field(default=1, alias="pageNo", description="字段所在页码，从 1 开始")
    validation_status: Literal["PASSED", "FAILED"] = Field(
        alias="validationStatus", description="自动字段格式或业务规则校验结果"
    )
    review_required: bool = Field(
        default=False,
        alias="reviewRequired",
        description="置信度不足或规则校验失败时为 true，需要人工复核",
    )
    validation_message: str | None = Field(
        default=None,
        alias="validationMessage",
        description="规则校验失败时的说明；校验通过时为 null",
    )


class FieldCorrectionRequest(ApiModel):
    value: str = Field(min_length=1, max_length=128, description="人工确认后的字段值")
    operator_id: str = Field(
        alias="operatorId",
        min_length=1,
        max_length=64,
        description="执行修订的业务用户标识",
    )
    reason: str | None = Field(
        default=None,
        max_length=256,
        description="修订原因；建议记录原图复核结论",
    )


class FieldRevision(ApiModel):
    revision_id: str = Field(alias="revisionId", description="字段修订记录唯一标识")
    task_id: str = Field(alias="taskId", description="关联的 OCR 任务标识")
    field_code: str = Field(alias="fieldCode", description="被修订的业务字段编码")
    previous_value: str = Field(alias="previousValue", description="修订前字段值")
    current_value: str = Field(alias="currentValue", description="修订后字段值")
    operator_id: str = Field(alias="operatorId", description="执行修订的业务用户标识")
    reason: str | None = Field(default=None, description="修订原因")
    created_at: datetime = Field(alias="createdAt", description="修订记录创建时间")


class OcrTask(ApiModel):
    task_id: str = Field(alias="taskId", description="OCR 任务唯一标识")
    status: Literal[
        "PENDING",
        "PREPROCESSING",
        "RECOGNIZING",
        "SUCCEEDED",
        "FAILED",
    ] = Field(description="任务当前状态")
    source_file: SourceFile = Field(alias="sourceFile", description="原始上传文件信息")
    detection_preview: DetectionPreview | None = Field(
        default=None,
        alias="detectionPreview",
        description="带 OCR 文本检测框、序号、置信度和识别文本的派生效果图",
    )
    engine_version: str = Field(
        default="unknown",
        alias="engineVersion",
        description="执行本次识别的 OCR 引擎或模型版本，用于结果追溯与评测分组",
    )
    processing_duration_ms: float = Field(
        default=0.0,
        alias="processingDurationMs",
        ge=0,
        description="从进入识别引擎到生成任务结果的耗时，单位为毫秒",
    )
    text_blocks: list[TextBlock] = Field(
        default_factory=list, alias="textBlocks", description="OCR 识别出的文本块列表"
    )
    fields: list[FieldResult] = Field(
        default_factory=list,
        description="documentType=employee_profile 时返回映射字段；未指定材料类型时为空",
    )
    error_message: str | None = Field(
        default=None, alias="errorMessage", description="识别失败原因；成功时为 null"
    )
    created_at: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        alias="createdAt",
        description="任务创建时间",
    )
    updated_at: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        alias="updatedAt",
        description="任务最近更新时间",
    )


class OcrTaskPage(ApiModel):
    """OCR 任务分页查询结果。"""

    items: list[OcrTask] = Field(description="当前页的 OCR 任务，按创建时间倒序")
    total: int = Field(ge=0, description="符合查询条件的任务总数")
    page: int = Field(ge=1, description="当前页码，从 1 开始")
    page_size: int = Field(alias="pageSize", ge=1, description="每页任务数量")


class OcrStatistics(ApiModel):
    """本地 OCR 任务运行情况汇总。"""

    total_tasks: int = Field(alias="totalTasks", ge=0, description="任务总数")
    succeeded_tasks: int = Field(alias="succeededTasks", ge=0, description="识别成功任务数")
    failed_tasks: int = Field(alias="failedTasks", ge=0, description="识别失败任务数")
    review_required_tasks: int = Field(
        alias="reviewRequiredTasks",
        ge=0,
        description="至少包含一个待人工复核字段的任务数",
    )
    field_revision_count: int = Field(
        alias="fieldRevisionCount", ge=0, description="人工字段修订记录总数"
    )
    failure_reasons: dict[str, int] = Field(
        alias="failureReasons", description="失败原因及其出现次数"
    )


class OcrEvaluationSample(ApiModel):
    """一条脱敏评测样本的真值标注；接口仅即时计算，不持久化真值。"""

    task_id: str = Field(alias="taskId", min_length=1, description="待评测的 OCR 任务标识")
    clarity: Literal["HIGH", "MEDIUM", "LOW"] = Field(
        description="样本清晰度分组，用于比较不同图像质量下的识别表现"
    )
    expected_fields: dict[str, str] = Field(
        alias="expectedFields",
        min_length=1,
        description="人工标注的字段真值，键为字段编码，值为脱敏后的期望结果",
    )


class OcrEvaluationRequest(ApiModel):
    samples: list[OcrEvaluationSample] = Field(
        min_length=1,
        max_length=100,
        description="需要统计的脱敏评测样本，单次最多 100 条",
    )


class OcrEvaluationMetrics(ApiModel):
    sample_count: int = Field(alias="sampleCount", ge=0, description="样本数")
    expected_field_count: int = Field(alias="expectedFieldCount", ge=0, description="真值字段数")
    detected_field_count: int = Field(alias="detectedFieldCount", ge=0, description="检测到的真值字段数")
    correct_field_count: int = Field(alias="correctFieldCount", ge=0, description="与真值一致的字段数")
    field_detection_rate: float = Field(alias="fieldDetectionRate", ge=0, le=1, description="字段检出率")
    field_accuracy: float = Field(alias="fieldAccuracy", ge=0, le=1, description="字段准确率")
    failed_task_count: int = Field(alias="failedTaskCount", ge=0, description="识别失败任务数")
    failure_rate: float = Field(alias="failureRate", ge=0, le=1, description="任务失败率")
    average_processing_duration_ms: float = Field(
        alias="averageProcessingDurationMs",
        ge=0,
        description="平均 OCR 处理耗时，单位为毫秒",
    )


class OcrEvaluationGroup(OcrEvaluationMetrics):
    clarity: Literal["HIGH", "MEDIUM", "LOW"] = Field(description="清晰度分组")


class OcrEvaluationResult(OcrEvaluationMetrics):
    """整批评测汇总，并按图像清晰度拆分结果。"""

    by_clarity: list[OcrEvaluationGroup] = Field(
        alias="byClarity", description="按图像清晰度分组的评测结果"
    )


def dump_model(value: BaseModel) -> dict:
    return value.model_dump(mode="json", by_alias=True)
