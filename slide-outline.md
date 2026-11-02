# Slide Outline

## Meta
- Topic: 旺旺金融 P2P 信贷撮合系统 —— 基于 Spring Cloud 微服务的借贷撮合平台设计与实现
- Scenario: 本科毕业答辩（15 分钟讲 + 5 分钟答问），完整版
- Content Source: provided（项目源码、毕业论文、业务概述、压测报告、部署方案）
- Style: Business Brown 温暖商务极简（简约大气）—— 奶油底 #FAF7F2 / 棕褐强调 #D4A574·#C4956A·#E8C9A0 / 封面与尾页深咖 #2B211A 整版；大型字体（标题 64px、正文 28px）+ 大留白 + 细线分隔 + 圆角白卡；重在"业务闭环"叙事，技术细节压缩为支撑
- Slide Count: 18
- Generated At: 2026-10-12T22:05:00+08:00

## Source Materials
- `D:\桌面\实训\报告\_pptsrc_report.txt` —— 毕业论文全文（摘要、需求、设计、实现、测试、结论）
- `D:\桌面\实训\报告\答辩PPT制作计划.md` —— 已有 18 页答辩蓝图（v2）
- `D:\桌面\实训\demo\docs\Jemeter\qps-report\QPS压测报告.md` —— 压测实测数据
- `D:\桌面\实训\demo\docs\微服务架构与启动说明.md`、`服务器部署与测试方案.md`、`合同体系对照核查.md`
- 源码结构：api(8990) / message(8120) / oss(8130) / ai(8081) / gateway(8088)；Nacos 8848；银行模拟服务 9090
- `D:\桌面\实训\demo\docs\shots\*.png` —— 10 张真实业务界面截图（本次按占位图处理）

## Slide-by-Slide Outline
1. **Slide 1 — Cover（封面）** — 项目名 + 一句话技术定位 + 署名 XXX 占位 | Layout: L01 中心堆叠 | 深咖整版 | Image: no | Chart: no
2. **Slide 2 — Background（选题背景与业务价值）** — 传统借贷三道坎，逐一搬掉：信息不对称 / 流程不透明 / 风控与信任缺失 | Layout: L07 3 栏卡片 | Image: no | Chart: no
3. **Slide 3 — Roles（系统角色与功能边界）** — 四类角色各干各的事：游客 / 借款人 / 投资人 / 管理员 | Layout: L15 2×2 | Image: no | Chart: no
4. **Slide 4 — Business Flow（一笔借款的完整旅程）** — 八步资金闭环：发标→投标→满标→签放款确认书→放款→分期还款→结清 | Layout: L13 流程带（SVG） | Image: no | Chart: yes(SVG时序)
5. **Slide 5 — Architecture（系统总体架构）** — 一个网关 + 五类微服务，平台不碰钱 | Layout: L05 左文右图（SVG架构图） | Image: no | Chart: yes(SVG架构)
6. **Slide 6 — Database（数据库设计）** — 34 张表归成四条主线；银行库 5 张为余额真源 | Layout: L15 2×2 | Image: no | Chart: no
7. **Slide 7 — Modules（模块划分与端口）** — 7 个后端模块 + 2 个前端工程，边界清晰（表格） | Layout: L19 表格 | Image: no | Chart: no
8. **Slide 8 — Core ① 借款与投标** — 发标到成标，状态机管住每一步（8 态） | Layout: L17 图+要点 | Image: yes(占位) | Chart: yes(SVG状态机)
9. **Slide 9 — Core ② 合同签署** — 三道关口签五类合同，电子签章全程留痕 | Layout: L13 关口流程 | Image: yes(占位) | Chart: yes(SVG)
10. **Slide 10 — Core ③ 还款与资金流转** — 每个动作留一条流水，钱去哪都能查 | Layout: L05 左文右图 | Image: yes(占位) | Chart: yes(SVG收银台流程)
11. **Slide 11 — Core ④ 风控与对账** — 事前控额度，事后自动对账 | Layout: L08 左右对比 | Image: no | Chart: no
12. **Slide 12 — APIs（核心接口说明）** — 6 个代表性接口一览（表格） | Layout: L19 表格 | Image: no | Chart: no
13. **Slide 13 — Dev Env（开发环境）** — 双 JDK 并存，前后端分离 | Layout: L07 3 栏 | Image: no | Chart: no
14. **Slide 14 — Deployment（部署方案）** — 阿里云 ECS + Nginx 反代 + Docker 容器化 + 演示期穿透 | Layout: L13 四段流程 | Image: no | Chart: yes(SVG部署图)
15. **Slide 15 — Highlight ① Redis** — 缓存 / 分布式锁 / 限流 / 登录态，四两拨千斤 | Layout: L16 4 图标 | Image: no | Chart: no
16. **Slide 16 — Highlight ② AI 与工程化** — AI 客服 + 可插拔存储 + 容器化提效 | Layout: L16 3 栏 | Image: no | Chart: no
17. **Slide 17 — Testing（系统测试）** — 功能全通过；40 并发 637 QPS、错误率 0.00%；纯读 80 并发 1462 QPS | Layout: L11 KPI + L17 柱状图 | Image: no | Chart: yes(SVG柱状)
18. **Slide 18 — Closing（总结与致谢）** — 完成了什么、还差什么、下一步；谢谢 + Q&A | Layout: L20 中心堆叠 | 深咖整版 | Image: no | Chart: no

## Visual Rhythm Notes
- 深色强调页（深咖整版）：Slide 1、Slide 18 —— 共 2 页，满足"至少 1–2 页深色"的节奏要求
- 图表承载页：Slide 4、5、8、9、10、14、17 —— 均由矢量 SVG 绘制，一图一结论
- 图片页：Slide 8/9/10 各留一处真实界面截图位（本次按占位块交付，可直接替换为 `docs/shots/*.png`）
- 幻灯片在大屏远距离观看，正文 ≥28px、标签 ≥24px、标题 ≥64px，关键数字 120–160px；每页一个大结论标题
- 每页 ≥2 个装饰元素（棕褐渐变圆、驼色方块图标、细线分隔、圆角白卡），避免"文字贴在矩形上"的观感
- 内容口径以论文为准：22 个测试类 / 147 条用例；压测数据以 `QPS压测报告.md` 实测为准（同机压测，偏保守）
