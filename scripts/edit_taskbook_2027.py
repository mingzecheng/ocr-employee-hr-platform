from copy import deepcopy
from pathlib import Path

from docx import Document
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT, WD_ROW_HEIGHT_RULE
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Pt


SOURCE = Path("/tmp/ocr-hr-taskbook-2027/2-毕业设计任务书-适用2027届.docx")
TARGET = Path("docs/2027届毕业设计/2-毕业设计任务书-适用2027届-完善版.docx")


TITLE = "基于 OCR 的企业员工档案与人事流程管理平台设计与实现"
FONT_NAME = "Songti SC"

PURPOSE = [
    ("选题的目的及意义", True),
    (
        "背景：企业员工档案涉及入职材料、劳动合同、岗位信息、异动记录、请假审批、离职交接等多类资料。实际管理中，纸质扫描件、图片和电子表格往往分散保存，档案分类、版本确认、借用归还和审批记录缺少统一关联；人工录入重复工作量较大，材料缺件、字段填写不一致和历史版本难以追溯等问题会影响人事业务办理效率。部分系统只关注员工信息维护，未能把档案资源的申请授权、使用留痕、归还盘点和异常处理连接起来，也缺少对 OCR 结果的证据保留和人工复核机制。",
        False,
    ),
    (
        "目的：本课题面向企业员工、部门负责人和人力资源管理人员，设计并实现一个基于 OCR 的员工档案与人事流程管理平台。系统以员工档案为核心对象，建立组织、部门、岗位、账号、材料、版本、合同和审批记录之间的关系，形成资源建档、申请授权、使用借用、变更维护、归还盘点和归档统计的闭环。平台调用 PaddleOCR 对扫描件或图片进行文字识别，在版面预处理后完成关键字段映射、坐标保存、置信度计算和格式校验；对低置信度字段给出提示，支持人工修订，并保留原图、原始 OCR 证据、修订内容和操作时间。通过入职、转岗、请假异动、离职交接等典型流程，验证档案数据与业务审批的联动关系。",
        False,
    ),
    (
        "意义：在实践层面，平台能够减少重复录入，帮助人事人员快速检索员工档案和合同状态，帮助部门负责人按数据范围处理审批，降低档案借用超期、材料缺失和版本不一致的管理风险；操作日志、traceId 和异常处理机制可以为问题定位和责任追踪提供依据。系统采用模拟或脱敏数据，按最小必要原则配置角色权限，不采集与业务无关的个人信息，具有较明确的数据治理边界。在工程学习层面，课题综合运用 Spring Boot、MyBatis、MySQL、Flyway、Vue 3、TypeScript、FastAPI 和 PaddleOCR，覆盖需求分析、数据库建模、接口设计、前后端协作、智能识别、测试验证和文档整理等软件工程环节。通过高、中、低清晰度样例统计字段准确率、检出率、失败率和平均处理耗时，可对 OCR 功能进行量化评估，为后续改进提供依据。",
        False,
    ),
]

MAIN_CONTENT = [
    ("设计的主要内容", True),
    ("1. 设计用户、组织、部门、岗位、员工、档案材料、版本、合同、审批申请、访问授权、使用记录、盘点任务和操作日志等核心数据模型，使用 MySQL 保存真实业务关系，使用 Flyway 管理数据库版本。", False),
    ("2. 实现员工档案建档、材料分类、原图上传、文件元数据登记、SHA-256 校验、版本新增、历史版本查询和归档检索，记录材料的来源、状态、变更时间及责任账号。", False),
    ("3. 实现 PaddleOCR 识别服务及前后端调用链，完成图像预处理、版面分析、字段映射、坐标和置信度保存、日期与编号格式校验、低置信度提示、人工修订和修订历史查询，保留原图与原始 OCR 证据。", False),
    ("4. 实现入职、转岗调动、请假异动和离职交接流程，支持员工或经办人员提交申请、部门负责人审批、人事复核、状态流转、有效日期和异常处理，并将流程记录关联到员工档案。", False),
    ("5. 实现合同台账、续签状态、档案访问或借用申请、授权、使用、归还、移交、超期提醒和变更留痕；实现盘点任务、缺件、损坏、版本不符等异常登记及闭环处理。", False),
    ("6. 实现 JWT 身份认证、角色权限、部门数据范围控制、BCrypt 密码存储、组合条件查询、分页查询、统计看板、操作日志、traceId 和统一异常处理，保证不同角色只能访问其业务范围内的数据。", False),
    ("7. 采用模拟或脱敏数据完成典型场景验证，设计核心接口和异常流程测试；按高、中、低三种清晰度样例统计 OCR 字段准确率、检出率、失败率和平均处理耗时，并整理需求、设计、数据库、接口、测试和部署文档。", False),
]

REQUIREMENTS = [
    ("设计的要求（包括技术要求、工作要求）", True),
    ("一、技术要求：后端使用 Spring Boot 3、MyBatis 和 MySQL，采用 Flyway 管理数据库迁移；前端使用 Vue 3、TypeScript 和 Vite，按模块组织页面与接口调用；OCR 采用 PaddleOCR，并通过 FastAPI 独立服务提供识别能力。系统应建立员工、档案材料、版本、合同、流程、授权、使用、盘点和日志之间的外键或业务关联，接口应具备参数校验、分页、统一响应和异常处理。认证采用 JWT，密码使用 BCrypt，权限控制覆盖角色和部门数据范围。OCR 结果必须保存原图、字段值、坐标、置信度、校验状态、原始证据和人工修订记录；低置信度字段应可提示和复核。关键接口需覆盖正常、越权、重复提交、状态不允许、超期归还和缺件异常等测试场景。", False),
    ("二、工作要求：按照需求分析、方案设计、编码实现、测试验证和文档整理的流程开展工作，保持代码、数据库脚本、接口文档和测试记录同步更新。开发使用个人计算机及本地 Docker 基础设施，演示使用模拟或脱敏数据，不收集与业务无关的个人信息。应遵守学校毕业设计进度安排，主动记录问题、提交阶段成果，接受指导教师检查并根据反馈修改。提交内容应包括可运行源代码、数据库迁移脚本、OCR 服务、部署说明、测试报告、演示数据和毕业设计论文材料。", False),
]

REFERENCES = [
    ("参考文献", True),
    ("[1] DU Y, LI C, GUO R, et al. PP-OCRv2: Bag of Tricks for Ultra Lightweight OCR System[EB/OL]. arXiv, 2021.", False),
    ("[2] LI C, et al. PP-OCRv3: More Attempts for the Improvement of Ultra Lightweight OCR System[EB/OL]. arXiv, 2022.", False),
    ("[3] LI M, LV T, CHEN J, et al. TrOCR: Transformer-based Optical Character Recognition with Pre-trained Models[C]//Proceedings of the AAAI Conference on Artificial Intelligence. 2023, 37(11): 13076-13084.", False),
    ("[4] PADDLEPADDLE AUTHORS. PaddleOCR Documentation 2.8[EB/OL]. 2024.", False),
    ("[5] SPRING TEAM. Spring Boot Reference Documentation 3.3[EB/OL]. 2024.", False),
    ("[6] MYBATIS TEAM. MyBatis 3 User Guide[EB/OL]. 2024.", False),
    ("[7] 全国人民代表大会常务委员会. 中华人民共和国个人信息保护法[S]. 北京: 法律出版社, 2021.", False),
    ("[8] 国家市场监督管理总局, 国家标准化管理委员会. 信息安全技术 个人信息安全规范: GB/T 35273—2020[S]. 北京: 中国标准出版社, 2020.", False),
]

SCHEDULE = [
    ("第一阶段", "开题：明确研究范围、业务角色、核心流程和 OCR 评测指标，完成任务书与开题材料。", "2026年10月", "形成开题材料"),
    ("第二阶段", "需求分析：调研员工档案、人事审批和档案借用场景，确定功能边界、数据范围、权限规则和异常流程，完成需求分析与初步数据库设计。", "2026年11月-2027年1月", "完成需求与数据模型"),
    ("第三阶段", "方案设计：完成系统架构、模块划分、数据库表结构、接口契约、OCR 字段模型、角色权限和页面原型设计，提交设计阶段成果。", "2027年1月-2027年2月", "完成概要与详细设计"),
    ("第四阶段", "编码测试：实现 Spring Boot 后端、Vue 前端、FastAPI OCR 服务及数据库迁移脚本，完成档案、流程、合同、授权、盘点和统计功能，开展接口、权限和异常流程测试。", "2027年2月-2027年3月", "提交可运行版本"),
    ("第五阶段", "文档整理：补充 OCR 清晰度评测，整理准确率、检出率、失败率和耗时数据，完善测试报告、部署说明、接口文档、数据库说明和论文初稿。", "2027年3月-2027年4月", "提交论文与材料初稿"),
    ("第六阶段", "提交定稿、进行答辩：根据指导意见修改系统和论文，完成演示数据、答辩材料和最终归档，准备系统功能与 OCR 评测演示。", "2027年4月-5月", "完成定稿与答辩"),
]

TEACHER_OPINION = [
    ("六、指导教师意见", True),
    ("课题“基于 OCR 的企业员工档案与人事流程管理平台设计与实现”符合软件工程专业人才培养目标，具有综合实践性。设计围绕员工档案、人事审批、合同台账和档案借用等业务，重点实现版本留痕、权限控制、流程联动和 PaddleOCR 字段抽取，研究内容与题目一致。方案采用 Spring Boot、MyBatis、MySQL、Vue 3、FastAPI 和 PaddleOCR，技术路线清晰，具备实施条件。建议加强低置信度复核、异常流程测试和清晰度评测，按最小必要原则使用模拟或脱敏数据。经审阅，同意开题。", False),
    ("指导教师签名：____________________    年    月    日", False),
]

DEPARTMENT_OPINION = [
    ("七、所属系（教研室）意见", True),
    ("选题符合软件工程专业人才培养要求，设计覆盖员工档案、人事审批和 OCR 信息抽取，能够训练学生综合运用软件工程知识分析和解决实际问题的能力。技术方案合理，计划安排适当，同意开题。", False),
    ("负责人签名：____________________    年    月    日", False),
]

COLLEGE_OPINION = [
    ("八、学院意见", True),
    ("设计内容符合学院人才培养要求，同意开题。", False),
    ("主管院长签名：__________________    年    月    日", False),
]


def set_run_font(run, size=10.5, bold=False):
    run.font.name = FONT_NAME
    run._element.rPr.rFonts.set(qn("w:ascii"), FONT_NAME)
    run._element.rPr.rFonts.set(qn("w:hAnsi"), FONT_NAME)
    run._element.rPr.rFonts.set(qn("w:eastAsia"), FONT_NAME)
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = None


def set_cell_content(cell, items, size=10.5, align=WD_ALIGN_PARAGRAPH.LEFT, compact=False):
    cell.text = ""
    cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
    for index, (text, bold) in enumerate(items):
        paragraph = cell.paragraphs[0] if index == 0 else cell.add_paragraph()
        paragraph.alignment = align
        paragraph.paragraph_format.space_before = Pt(0)
        paragraph.paragraph_format.space_after = Pt(0 if compact else 2)
        paragraph.paragraph_format.line_spacing = 1.0 if compact else 1.08
        if text:
            run = paragraph.add_run(text)
            set_run_font(run, size=size, bold=bold)


def set_cell_text(cell, text, size=10.5, bold=False, align=WD_ALIGN_PARAGRAPH.LEFT, compact=False):
    set_cell_content(cell, [(text, bold)], size=size, align=align, compact=compact)


def set_row_at_least(row, height_inches):
    row.height = Pt(height_inches * 72)
    row.height_rule = WD_ROW_HEIGHT_RULE.AT_LEAST


def main():
    doc = Document(str(SOURCE))

    # Remove instructional paragraphs outside the tables while preserving the cover layout.
    for paragraph in doc.paragraphs:
        if paragraph.text.strip() in {"内容填写格式要求", "宋体,小四号,行间距1.15倍,段前0.5行,首行缩进2字符。"}:
            paragraph.text = ""

    cover = doc.tables[0]
    set_cell_text(cover.cell(0, 0), "题    目：", size=11)
    set_cell_text(cover.cell(0, 1), TITLE, size=11)
    set_cell_text(cover.cell(1, 0), "", size=11)
    set_cell_text(cover.cell(1, 1), "", size=11)
    set_cell_text(cover.cell(2, 0), "作    者：", size=11)
    set_cell_text(cover.cell(3, 0), "学   号：", size=11)
    set_cell_text(cover.cell(4, 0), "所属学院：", size=11)
    set_cell_text(cover.cell(4, 1), "计算机科学与工程学院", size=11)
    set_cell_text(cover.cell(5, 0), "专业年级：", size=11)
    set_cell_text(cover.cell(5, 1), "软件工程 2023级", size=11)
    set_cell_text(cover.cell(6, 0), "学校导师：", size=11)
    set_cell_text(cover.cell(6, 1), "", size=11)
    set_cell_text(cover.cell(6, 2), "职  称：", size=11)
    set_cell_text(cover.cell(6, 3), "", size=11)
    set_cell_text(cover.cell(7, 0), "企业导师：", size=11)
    set_cell_text(cover.cell(7, 1), "", size=11)
    set_cell_text(cover.cell(7, 2), "职  务：", size=11)
    set_cell_text(cover.cell(7, 3), "", size=11)
    set_cell_text(cover.cell(8, 0), "完成时间：", size=11)
    set_cell_text(cover.cell(8, 1), "2026年10月", size=11)

    basic = doc.tables[1]
    set_cell_text(basic.cell(0, 0), "毕业设计题目", size=11, bold=True, align=WD_ALIGN_PARAGRAPH.CENTER)
    set_cell_text(basic.cell(0, 2), TITLE, size=11, bold=True, align=WD_ALIGN_PARAGRAPH.CENTER)
    set_cell_text(basic.cell(1, 0), "题目类型", size=10.5, align=WD_ALIGN_PARAGRAPH.CENTER)
    set_cell_text(basic.cell(1, 1), "计算机软件设计", size=10.5, align=WD_ALIGN_PARAGRAPH.CENTER)
    set_cell_text(basic.cell(1, 3), "题目来源", size=10.5, align=WD_ALIGN_PARAGRAPH.CENTER)
    set_cell_text(basic.cell(1, 4), "学生自选题", size=10.5, align=WD_ALIGN_PARAGRAPH.CENTER)
    set_cell_text(basic.cell(2, 0), "毕业设计时间", size=10.5, align=WD_ALIGN_PARAGRAPH.CENTER)
    set_cell_text(basic.cell(2, 2), "2026年10月至2027年5月", size=10.5, align=WD_ALIGN_PARAGRAPH.CENTER)
    set_cell_content(basic.cell(3, 0), PURPOSE, size=10.5)
    set_cell_content(basic.cell(4, 0), MAIN_CONTENT, size=10.5)
    set_cell_content(basic.cell(5, 0), REQUIREMENTS, size=10.5)
    set_cell_content(basic.cell(6, 0), [("注：本任务书采用模拟或脱敏数据进行演示，涉及个人信息的字段按最小必要原则设计。", False)], size=9.5)

    detail = doc.tables[2]
    set_cell_content(detail.cell(0, 0), REFERENCES, size=9.5)
    set_cell_text(detail.cell(1, 0), "五、毕业设计进度安排", size=11, bold=True, align=WD_ALIGN_PARAGRAPH.CENTER)
    headers = ["阶段", "工作内容", "起止时间", "备注"]
    for i, text in enumerate(headers):
        set_cell_text(detail.cell(2, i), text, size=10, bold=True, align=WD_ALIGN_PARAGRAPH.CENTER)
    for row_index, (stage, work, period, note) in enumerate(SCHEDULE, start=3):
        set_cell_text(detail.cell(row_index, 0), stage, size=8.6, align=WD_ALIGN_PARAGRAPH.CENTER, compact=True)
        set_cell_text(detail.cell(row_index, 1), work, size=8.6, compact=True)
        set_cell_text(detail.cell(row_index, 2), period, size=8.6, align=WD_ALIGN_PARAGRAPH.CENTER, compact=True)
        if row_index == 3:
            set_cell_text(detail.cell(row_index, 3), "各阶段按计划完成并保留阶段成果。", size=8.6, compact=True)
    set_cell_content(detail.cell(9, 0), TEACHER_OPINION, size=9.5, compact=True)
    set_cell_content(detail.cell(10, 0), DEPARTMENT_OPINION, size=9.5, compact=True)
    set_cell_content(detail.cell(11, 0), COLLEGE_OPINION, size=9.5, compact=True)

    # Let dense content expand instead of clipping inside the original template rows.
    for table in doc.tables:
        for row in table.rows:
            row.height_rule = WD_ROW_HEIGHT_RULE.AT_LEAST
            for cell in row.cells:
                cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER

    # Keep the cover metadata together on the first page. The second row is an
    # intentionally blank continuation line in the school template.
    cover_heights = [0.42, 0.16, 0.38, 0.38, 0.38, 0.38, 0.38, 0.38, 0.38]
    for row, height in zip(cover.rows, cover_heights):
        row.height = Pt(height * 72)
        row.height_rule = WD_ROW_HEIGHT_RULE.EXACTLY

    # Split the long template table before the three approval opinions so the
    # required sixth, seventh and eighth sections can remain on one page.
    detail_rows = list(detail._tbl.findall(qn("w:tr")))
    opinion_table = OxmlElement("w:tbl")
    for child in list(detail._tbl):
        if child.tag != qn("w:tr"):
            opinion_table.append(deepcopy(child))
    for row in detail_rows[9:]:
        detail._tbl.remove(row)
        opinion_table.append(row)
    body = detail._tbl.getparent()
    table_index = body.index(detail._tbl)
    page_break = OxmlElement("w:p")
    break_run = OxmlElement("w:r")
    break_element = OxmlElement("w:br")
    break_element.set(qn("w:type"), "page")
    break_run.append(break_element)
    page_break.append(break_run)
    body.insert(table_index + 1, page_break)
    body.insert(table_index + 2, opinion_table)
    for row, height in zip(detail_rows[9:], (2.25, 1.65, 1.2)):
        tr_pr = row.find(qn("w:trPr"))
        if tr_pr is None:
            tr_pr = OxmlElement("w:trPr")
            row.insert(0, tr_pr)
        tr_height = tr_pr.find(qn("w:trHeight"))
        if tr_height is None:
            tr_height = OxmlElement("w:trHeight")
            tr_pr.append(tr_height)
        tr_height.set(qn("w:val"), str(round(height * 1440)))
        tr_height.set(qn("w:hRule"), "atLeast")

    for paragraph in doc.paragraphs:
        for run in paragraph.runs:
            set_run_font(run, size=12)

    TARGET.parent.mkdir(parents=True, exist_ok=True)
    doc.save(str(TARGET))
    print(TARGET)


if __name__ == "__main__":
    main()
