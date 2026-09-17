// 后处理：补回被服务端剥离的 textAlign='left'，设置画布背景色
import { readFileSync, writeFileSync } from 'node:fs';

const f = new URL('./er-diagram.excalidraw', import.meta.url);
const scene = JSON.parse(readFileSync(f, 'utf8'));

let n = 0;
for (const el of scene.elements || []) {
  if (el.type === 'text' && el.textAlign !== 'left') { el.textAlign = 'left'; n++; }
}
scene.appState = { ...(scene.appState || {}), viewBackgroundColor: '#F7F6F5' };

writeFileSync(f, JSON.stringify(scene, null, 2));
console.log('textAlign 补回:', n, '| 背景: #F7F6F5');
