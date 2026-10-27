// SPDX-License-Identifier: GPL-3.0-only
package main

const page = `<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Shiraz APK runner</title>
<style>body{font:16px system-ui;max-width:760px;margin:3rem auto;padding:0 1rem;background:#151923;color:#f1f3f8}h1{font-size:2rem}form{padding:1.5rem;background:#232b3b;border-radius:12px}button{padding:.65rem 1.2rem;margin-left:.5rem;cursor:pointer}#state{margin:1rem 0}pre{background:#0b0e15;border-radius:8px;padding:1rem;min-height:14rem;max-height:60vh;overflow:auto;white-space:pre-wrap;overflow-wrap:anywhere}small{color:#abb6c9}</style>
<h1>Run an APK with Shiraz</h1><p id="state">Connecting…</p><form id="form"><input id="apk" type="file" name="apk" accept=".apk,application/vnd.android.package-archive" required><button id="send" disabled>Upload and run</button></form><p><small>The application opens on the Linux server's display. This page shows its run log.</small></p><pre id="log" aria-live="polite"></pre>
<script>
const state=document.querySelector('#state'), log=document.querySelector('#log'), send=document.querySelector('#send');
function connect(){const ws=new WebSocket((location.protocol==='https:'?'wss://':'ws://')+location.host+'/ws');ws.onopen=()=>{state.textContent='Connected';send.disabled=false};ws.onmessage=e=>{const msg=JSON.parse(e.data);if(msg.type==='status')state.textContent=msg.message;else{log.textContent+=msg.message+'\n';log.scrollTop=log.scrollHeight}};ws.onclose=()=>{state.textContent='Disconnected; reconnecting…';send.disabled=true;setTimeout(connect,1500)}}
connect();document.querySelector('#form').onsubmit=async e=>{e.preventDefault();send.disabled=true;log.textContent='';state.textContent='Uploading…';try{const response=await fetch('/upload',{method:'POST',body:new FormData(e.target)});if(!response.ok)throw Error(await response.text());state.textContent='APK accepted'}catch(error){state.textContent='Upload failed';log.textContent=error.message}finally{send.disabled=false}};
</script></html>`
