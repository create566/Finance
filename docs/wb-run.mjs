// 本地包装器：绕过 Node24 无法直接 spawn .cmd 的限制，用 node 直接执行 npx-cli.js
import { pathToFileURL } from 'node:url';
const cliUrl = pathToFileURL('c:/Users/zhangxiangxiang/.trae-cn/plugins/trae-remote-official/whiteboard/0.2.0/skills/whiteboard-design/scripts/canvas-cli.mjs').href;
const { runCanvasCli } = await import(cliUrl);

const code = await runCanvasCli(process.argv.slice(2), {
  executable: 'D:\\Node.js\\node.exe',
  prefixArgs: ['D:\\Node.js\\node_modules\\npm\\bin\\npx-cli.js', '-y', 'mcp-excalidraw-server@1.1.0']
});
process.exit(code);
