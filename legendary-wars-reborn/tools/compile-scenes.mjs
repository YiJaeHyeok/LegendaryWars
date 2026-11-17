// Static layout translation shared by Android and browser inspection.
export function compileScenes(layout,data){
  const scale=2.25,ox=100;
  const rect=(x,y,w,h)=>[ox+x*scale,720-(y+h)*scale,w*scale,h*scale];
  const resolveAtlas=(atlas,frame)=>data.atlases[atlas+'-hd']?.frames[frame]?atlas+'-hd':data.atlases[atlas]?.frames[frame]?atlas:null;
  const command=(atlas,frame,x,y,w,h)=>{const a=resolveAtlas(atlas,frame);return a?{atlas:a,frame,rect:rect(x,y,w,h)}:null;};
  const bg=[command('mainmenu-background','mainmenu-sky.png',-44,180,568,140),command('mainmenu-background','mainmenu-background-hills.png',-44,0,568,227)].filter(Boolean);
  const nodes=layout.menu.scenes.title.visuals,boxes=new Map();
  const locate=n=>{
    if(boxes.has(n.path))return boxes.get(n.path);
    const [w,h]=n.sourceSize??[0,0],anchor=n.anchor??[.5,.5];let x=0,y=0;
    const a=n.alignment;
    if(a&&a.horizontal!==null&&a.vertical!==null){
      x=480*a.horizontal;y=320*a.vertical;const ins=a.insets??[0,0];
      if(a.side==='inside'){x+=a.horizontal===1?-(ins[0]??0)-w*(1-anchor[0]):(ins[0]??0)+w*anchor[0];y+=a.vertical===1?-(ins[1]??0)-h*(1-anchor[1]):(ins[1]??0)+h*anchor[1];}
      else{x+=ins[0]??0;y+=ins[1]??0;}
    }else if(n.parent){const parent=nodes.find(v=>v.path===n.parent);const b=locate(parent);x=b.x+(n.position?.[0]??0);y=b.y+(n.position?.[1]??0);}
    else if(n.frame==='mainmenu-sword.png'){x=398;y=10;}
    else{x=(n.position?.[0]??240)-240;y=n.position?.[1]??0;}
    const b={x:x-w*anchor[0],y:y-h*anchor[1],w,h};boxes.set(n.path,b);return b;
  };
  const title={draw:[...bg],controls:[],derived:['Default anchors/cloth rendering and 960-wide composition centering require runtime visual comparison.']};
  // Preserve subtree draw order: negative children, node, positive children.
  function render(n){const children=nodes.filter(v=>v.parent===n.path).sort((a,b)=>a.z-b.z);for(const c of children.filter(v=>v.z<0))render(c);const b=locate(n),cmd=command(n.atlas,n.frame,b.x,b.y,b.w,b.h);if(cmd)title.draw.push(cmd);for(const c of children.filter(v=>v.z>=0))render(c);}
  for(const n of nodes.filter(v=>!v.parent).sort((a,b)=>a.z-b.z))render(n);
  title.controls.push({id:'props',rect:[100,70,1080,620]});
  const props={draw:[...bg],controls:[],derived:['Parent placement inferred from source sprite anchor; original polygon bounds retained.']};
  const left=layout.menu.scenes.props.recommendedBaseLeft;
  props.draw.push(command('mainmenu-foreground','mainmenu-props.png',left,0,905,320));
  for(const c of layout.menu.scenes.props.controls){
    if(!['campaign','mode'].includes(c.id))continue;
    const local=c.localBounds;if(local){const x=left+(c.position?.[0]??0)+local.x,y=(c.position?.[1]??0)+local.y;props.controls.push({id:c.id,rect:rect(x,y,local.width,local.height)});}
  }
  props.controls.push({id:'options',rect:rect(0,0,118,85)},{id:'title',rect:rect(400,0,80,65)});
  return {title,props:{...props,draw:props.draw.filter(Boolean)}};
}
