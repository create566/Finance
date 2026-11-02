# -*- coding: utf-8 -*-
"""修复 Redis 插入段的格式统一问题（标题/正文/导语/要点/表格/目录）"""
import copy
import shutil
import docx
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.enum.text import WD_ALIGN_PARAGRAPH

SRC = r'D:/桌面/实训/demo/output/safety-tables-20261016-082201/stage3/资金安全三机制速记表.docx'
BAK = r'D:/桌面/实训/demo/output/safety-tables-20261016-082201/stage3/资金安全三机制速记表.bak-before-redis-fix.docx'
shutil.copy2(SRC, BAK)
print('备份 ->', BAK)

doc = docx.Document(SRC)

def find_para(pred):
    for p in doc.paragraphs:
        try:
            if pred(p):
                return p
        except Exception:
            continue
    return None

def is_field_para(p):
    return p._p.find('.//' + qn('w:instrText')) is not None

def sub(parent, tag, **attrs):
    e = OxmlElement(tag)
    for k, v in attrs.items():
        e.set(qn('w:' + k), str(v))
    parent.append(e)
    return e

def replace_child(parent, old, new):
    if old is not None:
        parent.replace(old, new)
    else:
        parent.insert(0, new)

MSYH = '微软雅黑'
PFS = 'PingFang SC'

def rpr_body(sz, bold=None, font=MSYH, hint=True):
    """正文/表格 run 模板：微软雅黑或 PingFang SC + color 111827 + sz"""
    r = OxmlElement('w:rPr')
    kw = {'ascii': font, 'hAnsi': font, 'eastAsia': font}
    if hint:
        kw['hint'] = 'eastAsia'
    sub(r, 'w:rFonts', **kw)
    if bold is not None:
        sub(r, 'w:b', val='1' if bold else '0')
    sub(r, 'w:color', val='111827')
    sub(r, 'w:sz', val=str(sz))
    sub(r, 'w:lang', eastAsia='zh-CN')
    return r

# ---------- 模板段 ----------
h_tpl = find_para(lambda p: p.text.startswith('四、机制三') and not is_field_para(p))
assert h_tpl is not None, '未找到正文标题模板'
toc_tpl = find_para(lambda p: is_field_para(p) and '六、文件速记表' in p.text)
assert toc_tpl is not None, '未找到目录项模板'
note_tpl = find_para(lambda p: p.text.replace('\xa0', '').startswith('易错点') and not is_field_para(p))
assert note_tpl is not None, '未找到要点段模板'
tbl_tpl = doc.tables[0]
tbl4_tpl = doc.tables[3]

# ========== 1. 新标题段 ==========
head = find_para(lambda p: p.text == '五、Redis 的用武之地（两用一弃）')
assert head is not None, '未找到 Redis 标题'
# pPr：pStyle 000067 + 行距 312（对齐正文标题模板）
new_ppr = OxmlElement('w:pPr')
sub(new_ppr, 'w:pStyle', val='000067')
sub(new_ppr, 'w:spacing', line='312', lineRule='auto')
replace_child(head._p, head._p.find(qn('w:pPr')), new_ppr)
# run rPr：微软雅黑 15pt 非加粗 #111827（对齐正文标题模板）
run = head.runs[0]
replace_child(run._r, run._r.find(qn('w:rPr')), rpr_body(30, bold=False))
# 书签 section-5 / _Toc_section_5（id 11/12，结构对齐正文标题模板）
bs1 = OxmlElement('w:bookmarkStart'); bs1.set(qn('w:id'), '11'); bs1.set(qn('w:name'), 'section-5')
be1 = OxmlElement('w:bookmarkEnd');   be1.set(qn('w:id'), '11')
bs2 = OxmlElement('w:bookmarkStart'); bs2.set(qn('w:id'), '12'); bs2.set(qn('w:name'), '_Toc_section_5')
be2 = OxmlElement('w:bookmarkEnd');   be2.set(qn('w:id'), '12')
run._r.addprevious(bs1)
run._r.addprevious(be1)
run._r.addprevious(bs2)
run._r.addnext(be2)
print('1. 标题段格式已对齐 + 书签 section-5/_Toc_section_5')

# ========== 2. 拆分出的正文段行距 ==========
bodyp = find_para(lambda p: p.text.startswith('银行扣款类接口'))
assert bodyp is not None, '未找到拆分正文段'
bodyp.paragraph_format.line_spacing = 1.7
print('2. 正文段行距 1.7')

# ========== 3. 新导语段 ==========
lead = find_para(lambda p: p.text.startswith('Redis 在三机制里'))
assert lead is not None, '未找到新导语'
lead.paragraph_format.line_spacing = 1.7
for r in lead.runs:
    replace_child(r._r, r._r.find(qn('w:rPr')), rpr_body(18))
print('3. 导语段：行距 1.7 + 微软雅黑 9pt')

# ========== 4. 新要点段（callout 样式） ==========
note = find_para(lambda p: p.text.startswith('分布式锁三要点'))
assert note is not None, '未找到新要点段'
# pPr 深拷贝自原要点段（左边框 E65100 + 底纹 F3F4F6 + after 110 + line 408）
replace_child(note._p, note._p.find(qn('w:pPr')),
              copy.deepcopy(note_tpl._p.find(qn('w:pPr'))))
# 段首补 \xa0\xa0 缩进 run（PingFang SC 11pt，对齐原要点段）
nbsp_run = OxmlElement('w:r')
rPr = OxmlElement('w:rPr')
sub(rPr, 'w:rFonts', ascii=PFS, hAnsi=PFS)
sub(rPr, 'w:color', val='111827')
sub(rPr, 'w:sz', val='22')
t = OxmlElement('w:t')
t.text = '\xa0\xa0'
nbsp_run.append(rPr)
nbsp_run.append(t)
note.runs[0]._r.addprevious(nbsp_run)
# run0 = 加粗引导语；run1 = 正文
runs = note.runs
replace_child(runs[0]._r, runs[0]._r.find(qn('w:rPr')), rpr_body(22, bold=True))
for r in runs[1:]:
    replace_child(r._r, r._r.find(qn('w:rPr')), rpr_body(22, bold=False))
print('4. 要点段：callout 边框底纹 + 微软雅黑 11pt + 段首缩进')

# ========== 5. 新表格 ==========
t6 = doc.tables[6]
assert t6.rows[0].cells[0].text.strip() == '用途', '表6 定位异常'
# tblPr / tblGrid 深拷贝自原表
replace_child(t6._tbl, t6._tbl.find(qn('w:tblPr')),
              copy.deepcopy(tbl_tpl._tbl.find(qn('w:tblPr'))))
replace_child(t6._tbl, t6._tbl.find(qn('w:tblGrid')),
              copy.deepcopy(tbl4_tpl._tbl.find(qn('w:tblGrid'))))
# 行0 去 tblHeader（原表无）
trPr = t6.rows[0]._tr.find(qn('w:trPr'))
if trPr is not None:
    for th in trPr.findall(qn('w:tblHeader')):
        trPr.remove(th)
    if len(trPr) == 0:
        t6.rows[0]._tr.remove(trPr)
# 单元格 tcPr + run rPr + 段落
for ri, row in enumerate(t6.rows):
    for cell in row.cells:
        tcPr = OxmlElement('w:tcPr')
        sub(tcPr, 'w:tcW', w='2160', type='dxa')
        b = sub(tcPr, 'w:tcBorders')
        for side in ('top', 'left', 'bottom', 'right'):
            sub(b, 'w:' + side, val='single', color='E5E7EB', sz='6', space='0')
        if ri == 0:
            sub(tcPr, 'w:shd', val='clear', color='auto', fill='F3F4F6')
        m = sub(tcPr, 'w:tcMar')
        for side in ('top', 'left', 'bottom', 'right'):
            sub(m, 'w:' + side, w='120', type='dxa')
        sub(tcPr, 'w:vAlign', val='center')
        replace_child(cell._tc, cell._tc.find(qn('w:tcPr')), tcPr)
        for p in cell.paragraphs:
            p.paragraph_format.line_spacing = 1.0
            p.alignment = WD_ALIGN_PARAGRAPH.LEFT
            for r in p.runs:
                replace_child(r._r, r._r.find(qn('w:rPr')), rpr_body(18, bold=(ri == 0), font=PFS))
print('5. 表格：100% 宽 + E5E7EB 单元格边框 + 表头底纹 + PingFang SC 9pt')

# ========== 6. 目录 ==========
h6 = find_para(lambda p: p.text.startswith('六、文件速记表') and not is_field_para(p))
assert h6 is not None, '未找到六正文标题'
for bm in h6._p.findall(qn('w:bookmarkStart')):
    n = bm.get(qn('w:name'))
    if n == 'section-5':
        bm.set(qn('w:name'), 'section-6')
    elif n == '_Toc_section_5':
        bm.set(qn('w:name'), '_Toc_section_6')
toc6 = toc_tpl
for it in toc6._p.findall('.//' + qn('w:instrText')):
    if it.text and '_Toc_section_5' in it.text:
        it.text = 'HYPERLINK \\l "_Toc_section_6"'
# 新目录项插在六项目之前
new_toc = copy.deepcopy(toc6._p)
for it in new_toc.findall('.//' + qn('w:instrText')):
    if it.text and '_Toc_section_6' in it.text:
        it.text = 'HYPERLINK \\l "_Toc_section_5"'
for t in new_toc.findall('.//' + qn('w:t')):
    t.text = '五、Redis 的用武之地（两用一弃）'
toc6._p.addprevious(new_toc)
print('6. 目录：新增五项 + 六项书签/链接重编号')

doc.save(SRC)
print('已保存 ->', SRC)
