// 生成旺旺金融 ER 图元素 JSON（基于数据库真实 18 张表结构，brand-default 合规）
import { writeFileSync } from 'node:fs';

let seq = 0;
const rid = (p) => `${p}_${(++seq).toString().padStart(3, '0')}`;
const rand = () => Math.floor(Math.random() * 2e9);

const C = {
  zoneStroke: '#7373732E', zoneFill: '#FAFAFA',
  entStroke: '#7373735C', entFill: '#FFFFFF',
  coreStroke: '#654ACB', coreFill: '#EEEFFF',
  mainStroke: '#654ACB', mainFill: '#654ACB',
  text: '#0A0A0A', muted: '#404040', white: '#FFFFFF',
  line: '#737373', accent: '#654ACB',
};

const base = (over) => ({
  angle: 0, strokeColor: C.entStroke, backgroundColor: C.entFill, fillStyle: 'solid',
  strokeWidth: 1, strokeStyle: 'solid', roughness: 0, opacity: 100, groupIds: [],
  strokeSharpness: 'sharp', seed: rand(), version: 1, versionNonce: rand(),
  isDeleted: false, boundElements: null, updated: 1, link: null, locked: false, ...over,
});

const FONT = 16, LH = 1.25, LINEPX = Math.round(FONT * LH); // 20

// 实体卡片
function entity(e) {
  const lines = [ `${e.id} · ${e.cn}`, ...e.fields ];
  const h = e.h;
  const rect = base({
    id: 'ent_' + e.id, type: 'rectangle', x: e.x, y: e.y, width: e.w, height: h,
    strokeColor: e.role === 'main' ? C.mainStroke : (e.role === 'core' ? C.coreStroke : C.entStroke),
    backgroundColor: e.role === 'main' ? C.mainFill : (e.role === 'core' ? C.coreFill : C.entFill),
    roundness: { type: 3, value: 8 },
    boundElements: [{ id: 'txt_' + e.id, type: 'text' }],
  });
  const th = lines.length * LINEPX;
  const text = base({
    id: 'txt_' + e.id, type: 'text', x: e.x + 10, y: e.y + (h - th) / 2,
    width: e.w - 20, height: th,
    strokeColor: e.role === 'main' ? C.white : C.text,
    backgroundColor: 'transparent',
    text: lines.join('\n'), fontSize: FONT, fontFamily: 'cascadia',
    textAlign: 'left', verticalAlign: 'middle', baseline: th - FONT + 3,
    containerId: 'ent_' + e.id, originalText: lines.join('\n'), lineHeight: LH,
    boundElements: [], roundness: null,
  });
  delete text.roundness;
  return [rect, text];
}

function freeText(id, s, x, y, size, color) {
  const w = Math.round(s.length * size * 0.62) + 20;
  return base({
    id, type: 'text', x, y, width: Math.max(w, 200), height: Math.round(size * 1.4),
    strokeColor: color, backgroundColor: 'transparent',
    text: s, fontSize: size, fontFamily: 'helvetica', textAlign: 'left',
    verticalAlign: 'top', baseline: size, containerId: null, originalText: s,
    lineHeight: 1.25, boundElements: [], roundness: null,
  });
}

function zone(id, x, y, w, h) {
  return base({ id, type: 'rectangle', x, y, width: w, height: h,
    strokeColor: C.zoneStroke, backgroundColor: C.zoneFill, strokeStyle: 'dashed',
    roundness: { type: 3, value: 12 } });
}

const ENT = {};
const center = (id) => {
  const e = ENT[id];
  return { x: e.x + e.w / 2, y: e.y + e.h / 2 };
};

// 无向 ER 关联线（elbowed 自动绕行），基数标签为白底芯片（位置显式给定，避开卡片）
function rel(from, to, card, pos) {
  const els = [];
  const aid = 'rel_' + from + '__' + to;
  const a = center(from), b = center(to);
  const x = Math.min(a.x, b.x), y = Math.min(a.y, b.y);
  els.push(base({
    id: aid, type: 'arrow', x, y,
    width: Math.abs(b.x - a.x) || 20, height: Math.abs(b.y - a.y) || 20,
    strokeColor: C.line, strokeWidth: 1,
    points: [[a.x - x, a.y - y], [b.x - x, b.y - y]], lastCommittedPoint: null,
    startBinding: { elementId: 'ent_' + from, focus: 0, gap: 8 },
    endBinding: { elementId: 'ent_' + to, focus: 0, gap: 8 },
    startArrowhead: null, endArrowhead: null, elbowed: true, roundness: null,
    boundElements: [],
  }));
  // 基数芯片：白底圆角矩形 + 左对齐文字
  const chipW = 38, chipH = 24;
  els.push(base({
    id: 'chip_' + from + '__' + to, type: 'rectangle',
    x: pos.x, y: pos.y, width: chipW, height: chipH,
    strokeColor: C.entStroke, backgroundColor: C.white,
    roundness: { type: 3, value: 6 }, boundElements: [{ id: 'lab_' + from + '__' + to, type: 'text' }],
  }));
  els.push(base({
    id: 'lab_' + from + '__' + to, type: 'text',
    x: pos.x + 7, y: pos.y + 3, width: chipW - 12, height: FONT + 4,
    strokeColor: C.accent, backgroundColor: 'transparent',
    text: card, fontSize: FONT, fontFamily: 'cascadia', textAlign: 'left',
    verticalAlign: 'middle', baseline: FONT, containerId: 'chip_' + from + '__' + to,
    originalText: card, lineHeight: LH, boundElements: [], roundness: null,
  }));
  return els;
}

const W = 280, CW = 300;
const out = [];
const addEnt = (e) => { ENT[e.id] = { x: e.x, y: e.y, w: e.w, h: e.h }; entity(e).forEach(x => out.push(x)); };

// ===== 标题（28/16） =====
out.push(freeText('title', '旺旺金融信贷系统 · 数据库 ER 图', 40, 20, 28, C.text));
out.push(freeText('subtitle', '共 18 张表，按 4 个业务域分组：用户域 / 认证借款域 / 标的交易域 / 支撑域；连线为外键关联，芯片标注 1:1 与 1:N 基数', 40, 62, 16, C.muted));

// ===== 分区背景与标题 =====
out.push(zone('zone_user', 24, 100, 1872, 200));
out.push(zone('zone_auth', 336, 320, 944, 224));
out.push(zone('zone_lend', 336, 564, 1336, 224));
out.push(zone('zone_sup', 104, 808, 1568, 184));
out.push(freeText('zt_user', '用户域（账户 / 绑卡 / 积分 / 登录）', 44, 108, 16, C.muted));
out.push(freeText('zt_auth', '实名认证 / 借款申请域', 356, 328, 16, C.muted));
out.push(freeText('zt_lend', '标的交易域（核心：标的 → 投资 → 还款 → 回款）', 356, 572, 16, C.accent));
out.push(freeText('zt_sup', '支撑域（流水 / 积分等级 / 字典 / 消息 / 展示）', 124, 816, 16, C.muted));

// ===== 行1 用户域  y=132 h=164 =====
const y1 = 132, h1 = 164;
[
  { id: 'user', cn: '用户表', x: 40, fields: ['PK id','   user_type 1出借/2借款','   mobile 手机号','   bind_status 绑卡状态','   borrow_auth_status 实名','   integral 积分'] },
  { id: 'user_account', cn: '资金账户', x: 352, fields: ['PK id','FK user_id','   amount 可用余额','   freeze_amount 冻结额'] },
  { id: 'user_bind', cn: '绑卡(存管账户)', x: 664, fields: ['PK id','FK user_id','   bank_no 银行卡号','   bind_code 存管协议号','   status 绑定状态'] },
  { id: 'user_integral', cn: '积分流水', x: 976, fields: ['PK id','FK user_id','   integral 积分变动','   content 说明'] },
  { id: 'user_login_record', cn: '登录记录', x: 1288, fields: ['PK id','FK user_id','   ip 登录IP'] },
  { id: 'sys_admin', cn: '管理员', x: 1600, fields: ['PK id','   username 账号','   name 姓名'] },
].forEach(e => addEnt({ ...e, y: y1, w: W, h: h1 }));

// ===== 行2 认证借款  y=356 h=184 =====
const y2 = 356, h2 = 184;
[
  { id: 'borrower', cn: '借款人认证', x: 352, fields: ['PK id','FK user_id','   name / id_card 实名信息','   education 学历','   income 月收入','   status 认证状态'] },
  { id: 'borrower_attach', cn: '认证附件', x: 664, fields: ['PK id','FK borrower_id','   image_type 图片类型','   image_url 图片地址'] },
  { id: 'borrow_info', cn: '借款申请', x: 976, fields: ['PK id','FK user_id','   amount 金额 period 期限','   borrow_year_rate 年化利率','   return_method 还款方式','   status 审批状态'] },
].forEach(e => addEnt({ ...e, y: y2, w: W, h: h2 }));

// ===== 行3 标的域  y=600 h=184（核心色，lend 实心紫） =====
const y3 = 600, h3 = 184;
[
  { id: 'lend', cn: '标的（核心）', x: 352, role: 'main', fields: ['PK id','   lend_no 标的编号','FK user_id 借款人','FK borrow_info_id','   amount / lend_year_rate','   invest_amount 已募集','   status 标的状态'] },
  { id: 'lend_item', cn: '投资记录', x: 684, role: 'core', fields: ['PK id','   lend_item_no 投资单号','FK lend_id','FK invest_user_id 投资人','   invest_amount 投资额','   expect_amount 预期收益','   status 状态'] },
  { id: 'lend_return', cn: '借款人还款计划', x: 1016, role: 'core', fields: ['PK id','   return_no 还款单号','FK lend_id','FK user_id 借款人','   current_period 期数','   principal/interest/total','   status / is_overdue'] },
  { id: 'lend_item_return', cn: '投资人回款计划', x: 1348, role: 'core', fields: ['PK id','FK lend_return_id','FK lend_item_id','FK invest_user_id 投资人','   current_period 期数','   principal/interest/total','   status 归还状态'] },
].forEach(e => addEnt({ ...e, y: y3, w: CW, h: h3 }));

// ===== 行4 支撑域  y=844 h=144 =====
const y4 = 844, h4 = 144;
[
  { id: 'trans_flow', cn: '交易流水', x: 120, fields: ['PK id','   trans_no 交易单号','FK user_id','   trans_type 交易类型','   trans_amount 金额'] },
  { id: 'integral_grade', cn: '积分等级/额度', x: 432, fields: ['PK id','   integral_start / end','   borrow_amount 可借额度'] },
  { id: 'dict', cn: '数据字典', x: 744, fields: ['PK id','FK parent_id','   dict_code / value'] },
  { id: 'message', cn: '站内消息', x: 1056, fields: ['PK id','FK user_id','   message_type 1催收2通知','   is_read 是否已读'] },
  { id: 'investment_list', cn: '投资列表(展示)', x: 1368, fields: ['PK id','   mobile / loan_title','   loan_money / loan_progress'] },
].forEach(e => addEnt({ ...e, y: y4, w: W, h: h4 }));

// ===== 关系 + 基数芯片（pos 为芯片左上角，显式避让卡片） =====
[
  ['user', 'user_account', '1:1', { x: 322, y: 202 }],
  ['user', 'user_bind',      '1:1', { x: 634, y: 202 }],
  ['user', 'borrower',       '1:1', { x: 556, y: 330 }],
  ['borrower', 'borrower_attach', '1:N', { x: 634, y: 436 }],
  ['user', 'borrow_info',   '1:N', { x: 1076, y: 330 }],
  ['borrow_info', 'lend',   '1:1', { x: 306, y: 680 }],
  ['lend', 'lend_item',     '1:N', { x: 650, y: 680 }],
  ['lend', 'lend_return',   '1:N', { x: 982, y: 680 }],
  ['lend_item', 'lend_item_return', '1:N', { x: 1476, y: 576 }],
  ['lend_return', 'lend_item_return', '1:N', { x: 1308, y: 680 }],
  ['user', 'lend_item',     '1:N', { x: 812, y: 576 }],
  ['user', 'lend_return',   '1:N', { x: 1136, y: 576 }],
  ['user', 'trans_flow',    '1:N', { x: 64, y: 904 }],
  ['user', 'message',       '1:N', { x: 1166, y: 820 }],
].forEach(([f, t, card, pos]) => rel(f, t, card, pos).forEach(x => out.push(x)));

writeFileSync(new URL('./er-elements.json', import.meta.url), JSON.stringify(out, null, 1));
console.log('生成元素数:', out.length);
