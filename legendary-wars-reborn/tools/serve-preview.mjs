import http from 'node:http';import fs from 'node:fs';import path from 'node:path';import{fileURLToPath}from'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const server=http.createServer((req,res)=>{
 try{const url=new URL(req.url,'http://127.0.0.1'),pathname=decodeURIComponent(url.pathname);let file;
 if(pathname==='/')file=path.join(root,'tools/preview.html');
 else if(/^\/assets\/(?:game-data\.json|art\/[A-Za-z0-9_.-]+\.png)$/.test(pathname))file=path.join(root,'app/src/main',pathname.slice(1));
 else{res.writeHead(404);res.end();return;}
 res.setHeader('Content-Type',file.endsWith('.png')?'image/png':file.endsWith('.json')?'application/json':'text/html; charset=utf-8');res.setHeader('Cache-Control','no-store');fs.createReadStream(file).on('error',()=>{res.statusCode=404;res.end();}).pipe(res);
 }catch{res.writeHead(400);res.end();}
});server.listen(4179,'127.0.0.1',()=>console.log('Layout preview http://127.0.0.1:4179'));
