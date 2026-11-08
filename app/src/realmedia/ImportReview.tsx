import {useEffect,useRef,useState} from "react";
import {View,Text,Pressable,ScrollView,Alert,StyleSheet,Switch,BackHandler,AppState} from "react-native";
import {copy,type CopyKey,type Language} from "./strings";
import {imports,listenImports,listenExport,listenDelivery,type ExportState,type DeliveryState,type Project,type Result,type PlanResult} from "./imports";
import {NativePreview,type PreviewState,type PreviewCommand} from "./NativePreview";
import {counts,clock,displayAspect,canCreateNative,mergeExportState,mergeDeliveryState,automaticInformationPanel,canCheckPlan,leaveDecision,voiceContinueKey,voiceHelperKey,errorCopyKey,type Screen} from "./flowState";
/** Product flow shell. Shipping diagnostic entry unchanged. No renderer/player/creation claim. */
export default function ImportReview(){
 const [p,setP]=useState<Project|null>(null),[screen,setScreen]=useState<Screen>("start"),[lang,setLang]=useState<Language>(Intl.DateTimeFormat().resolvedOptions().locale.startsWith("he")?"he":"en");
 const [busy,setBusy]=useState(false),[status,setStatus]=useState(""),[plan,setPlan]=useState<PlanResult|null>(null),[clip,setClip]=useState(0);
 const [preview,setPreview]=useState<PreviewState>({state:"PREVIEW_STOPPED",positionUs:0,durationUs:0,clipPositionUs:null,canMark:false,canEndMark:false});
 const [command,setCommand]=useState<PreviewCommand>({action:"none",nonce:0});
 const sendPreview=(action:string,extra:Partial<PreviewCommand>={})=>setCommand(old=>({...extra,action,nonce:old.nonce+1}));
 const [delivery,setDelivery]=useState<DeliveryState|null>(null),[panel,setPanel]=useState<"none"|"info"|"first-share"|"reminder">("none"),[deliveryActionBusy,setDeliveryActionBusy]=useState(false);
 const deliveryId=useRef("");
 const [shareFailed,setShareFailed]=useState(false),[shareOpening,setShareOpening]=useState(false);
 useEffect(()=>{const sub=AppState.addEventListener("change",state=>{const id=deliveryId.current;if(state==="active"&&id)imports.getDeliveryState(id).then(info=>{if(deliveryId.current===id)setDelivery(info);}).catch(()=>{});});return()=>sub.remove();},[]);
 const [filePlayerActive,setFilePlayerActive]=useState(false);
 const [job,setJob]=useState<ExportState>({state:"READY_TO_CREATE",active:false});
 useEffect(()=>{if(screen!=="plan")setFilePlayerActive(false);},[screen]);
 useEffect(()=>{const id=job.lastGood?.exportId??"";deliveryId.current=id;setPanel("none");setDelivery(null);setShareFailed(false);if(id)imports.getDeliveryState(id).then(info=>{if(deliveryId.current!==id)return;setDelivery(info);if(automaticInformationPanel(info))setPanel("info");}).catch(()=>{});},[job.lastGood?.exportId]);
 const save=async()=>{const id=job.lastGood?.exportId;if(!id||deliveryActionBusy||delivery?.active)return;setDeliveryActionBusy(true);setDelivery(old=>({...old,exportId:id,saveState:"SAVING",active:true}));try{const r=await imports.saveExport(id);if(!r.ok)setDelivery(old=>({...old,exportId:id,saveState:"SAVE_FAILED",active:false}));}catch{setDelivery(old=>({...old,exportId:id,saveState:"SAVE_FAILED",active:false}));}finally{setDeliveryActionBusy(false);}};
 const share=async(continueFirst=false)=>{const id=job.lastGood?.exportId;if(!id||deliveryActionBusy||delivery?.active)return;setDeliveryActionBusy(true);setShareFailed(false);setShareOpening(true);try{const r=await imports.shareExport(id,continueFirst);if(deliveryId.current!==id)return;if(r.state==="NEEDS_NOTICE")setPanel("first-share");else if(r.state==="CHOOSER_OPENED"){setPanel("none");imports.getDeliveryState(id).then(info=>{if(deliveryId.current===id)setDelivery(info);}).catch(()=>{});}else if(r.state==="SHARE_FAILED")setShareFailed(true);}catch{setShareFailed(true);}finally{setShareOpening(false);setDeliveryActionBusy(false);}};
 const presented=()=>{const id=job.lastGood?.exportId;if(id)imports.informationPresented(id).then(info=>{if(deliveryId.current===id&&info.infoShown)setDelivery(old=>mergeDeliveryState(old,info,id));}).catch(()=>{});};
 const [progress,setProgress]=useState({done:0,total:0});
 const [undo,setUndo]=useState<{id:string;included:boolean}|null>(null);const lock=useRef(false);const rtl=lang==="he";
 const c=(key:CopyKey,values:Record<string,string|number>={})=>copy(key,lang,values);
 // Temporary honest implementation-status copy; Designer keys do not cover unwired playback/create.
 const note=(en:string,he:string)=>rtl?he:en;
 const labelStyle={textAlign:rtl?"right" as const:"left" as const,writingDirection:rtl?"rtl" as const:"ltr" as const};
 const apply=(r:Result)=>{if(r.project)setP(r.project);setPlan(null);setStatus(r.code??r.results?.find(x=>x.code!=="READY")?.code??(r.ok?"READY":"COPY_FAILED"));};
 const run=async(fn:()=>Promise<Result>,next?:Screen)=>{if(lock.current)return;lock.current=true;setBusy(true);try{const r=await fn();apply(r);if(r.ok&&next)setScreen(next);}catch{setStatus("COPY_FAILED");}finally{lock.current=false;setBusy(false);}};
 useEffect(()=>{const ds=listenDelivery(ev=>setDelivery(old=>mergeDeliveryState(old,ev,deliveryId.current)));const ex=listenExport(ev=>{setJob(old=>mergeExportState(old,ev));if(ev.state!=="READY_TO_CREATE")setScreen("plan");});imports.getExportState().then(setJob).catch(()=>{});const sub=listenImports(ev=>{setStatus(ev.state);setProgress({done:ev.selectionIndex+(ev.state==="READY"?1:0),total:ev.selectionTotal});});imports.loadProject().then(apply).catch(()=>setStatus("COPY_FAILED"));return()=>{sub.remove();ex.remove();ds.remove();};},[]);
 const editStoredMark=(action:"discard"|"remove",rangeId?:string)=>{
  if(screen==="voices"&&current&&song)sendPreview(action,rangeId?{rangeId}:{});
  else run(()=>action==="discard"?imports.discardPendingVoiceStart():imports.removeVoiceRange(rangeId!));
 };
 const leave=(next:Screen)=>{if(job.active||delivery?.active||deliveryActionBusy)return;if(p&&leaveDecision(p)==="finish-or-discard")Alert.alert(c("voices.pending_mark"),undefined,[{text:c("voices.pending_finish"),onPress:()=>setScreen("voices")},{text:c("voices.pending_discard"),style:"destructive",onPress:()=>{editStoredMark("discard");setScreen("voices");}}]);else setScreen(next);};
 useEffect(()=>{const sub=BackHandler.addEventListener("hardwareBackPress",()=>{if(delivery?.active){imports.cancelSave();return true;}if(deliveryActionBusy)return true;if(job.active){imports.cancelExport();return true;}if(busy)return true;if(screen!=="start"){leave(screen==="plan"?"voices":"start");return true;}if(p?.pendingVoiceStart){leave("start");return true;}return false;});return()=>sub.remove();},[screen,p,busy,lang,job.active,delivery?.active,deliveryActionBusy]);
 const button=(title:string,action:()=>void,disabled=false)=><Pressable accessibilityRole="button" accessibilityLabel={title} accessibilityState={{disabled:disabled||busy||job.active||!!delivery?.active||deliveryActionBusy}} disabled={disabled||busy||job.active||!!delivery?.active||deliveryActionBusy} onPress={action} style={[s.button,(disabled||busy||job.active||!!delivery?.active||deliveryActionBusy)&&s.disabled]}><Text style={labelStyle}>{title}</Text></Pressable>;
 const pick=(kind:"visual"|"song")=>{
   const go=()=>run(()=>imports.pickAndImport(kind),kind==="song"?"voices":"review");
   // Provider warning is distinct from app upload policy; system provider may download selections.
   Alert.alert(kind==="song"?c("song.title"):c("start.title"),kind==="song"?c("song.before_picker"):c("import.provider_warning"),[{text:c("export.cancel"),style:"cancel"},{text:kind==="song"?c("song.cta"):c("start.cta"),onPress:go}]);
 };
 const visual=p?.assets.filter(a=>a.kind!=="song")??[],videos=visual.filter(a=>a.kind==="video"&&a.included),song=p?.assets.find(a=>a.kind==="song");
 const current=videos[Math.min(clip,Math.max(0,videos.length-1))];const n=p?counts(p):{included:0,excluded:0,retained:0};
 const check=async()=>{if(!p||!canCheckPlan(p)){setStatus(p?.pendingVoiceStart?"INCOMPLETE_VOICE_RANGE":"SONG_TOO_SHORT");return;}if(lock.current)return;lock.current=true;setBusy(true);try{const r=await imports.planProject([]);setPlan(r);setStatus(r.ok?"":r.code??"COPY_FAILED");setScreen("plan");}catch{setStatus("COPY_FAILED");}finally{lock.current=false;setBusy(false);}};
 const statusText=()=>{if(!status)return "";if(status==="COPYING")return c("import.COPYING",{done:progress.done,total:progress.total});const k=errorCopyKey(status);try{return c(k as CopyKey,{over:Math.max(1,n.retained-30),duration:note("not available","לא זמין"),song:song?clock(song.durationUs):note("not selected","לא נבחר")});}catch{return c("import.COPY_FAILED");}};
 const placeholder=<View accessibilityRole="text" accessibilityLabel={note("Playback unavailable. No completed movie preview.","הניגון אינו זמין. זו אינה תצוגה מקדימה של סרט שהושלם.")} style={[s.frame,{aspectRatio:current?displayAspect(current.width,current.height,current.rotation):16/9}]}><Text style={s.frameText}>{c("playback.unavailable")}</Text></View>;
 return <ScrollView contentContainerStyle={[s.page,{direction:rtl?"rtl":"ltr"}]}>
  <Text style={[s.title,labelStyle]}>{c(screen==="start"?"start.title":screen==="review"?"review.title":screen==="song"?"song.title":screen==="voices"?"voices.title":"export.READY_TO_CREATE.duration",screen==="plan"?{duration:plan?.durationUs?clock(plan.durationUs):note("not available","לא זמין")}: {})}</Text>
  {button("English / עברית",()=>setLang(rtl?"en":"he"))}
  {!!status&&<Text accessibilityLiveRegion="polite" style={labelStyle}>{statusText()}</Text>}
  {busy&&<Pressable accessibilityRole="button" accessibilityLabel={c("import.cancel")} style={s.button} onPress={()=>imports.cancelImport()}><Text style={labelStyle}>{c("import.cancel")}</Text></Pressable>}
  {screen==="start"&&<>
   <Text style={labelStyle}>{c("start.body")}</Text>
   {p&&visual.length>0? <>{button(c("start.continue"),()=>setScreen("review"))}{button(c("start.new"),()=>Alert.alert(c("start.new"),c("start.replace_confirm"),[{text:c("start.replace_keep"),style:"cancel"},{text:c("start.replace_confirm_cta"),style:"destructive",onPress:()=>run(()=>imports.replaceProject("REPLACE_PROJECT"),"review") }]))}</>:button(c("start.cta"),()=>pick("visual"))}
  </>}
  {screen==="review"&&<>
   <Text style={labelStyle}>{c("review.counts",{included:n.included.toLocaleString(lang),excluded:n.excluded.toLocaleString(lang)})}</Text>
   <Text style={labelStyle}>{c("review.size",{size:`${((p?.totalSourceBytes??0)/1048576).toLocaleString(lang,{maximumFractionDigits:1})} MiB`})}</Text>
   <Text style={labelStyle}>{c("review.space_hint")}</Text>
   <View style={s.row}><Text style={labelStyle}>{c("review.order_toggle")}</Text><Switch accessibilityLabel={c("review.order_toggle")} accessibilityState={{checked:p?.keepDateOrder??true,disabled:busy}} disabled={busy} value={p?.keepDateOrder??true} onValueChange={v=>run(()=>imports.setDateOrder(v))}/></View>
   {visual.map((a,i)=><View key={a.id} style={s.card}>
    <Text style={labelStyle}>{(i+1).toLocaleString(lang)}{a.kind==="video"?` · \u2066${clock(a.durationUs)}\u2069`:""}</Text>
    {a.state!=="READY"&&<Text style={labelStyle}>{c("import.ACCESS_UNREADABLE")}</Text>}
    <View style={s.row}><Text style={labelStyle}>{c(a.included?"review.item_toggle_on":"review.item_toggle_off")}</Text><Switch accessibilityLabel={`${i+1} ${c("review.include_toggle")}`} accessibilityState={{checked:a.included,disabled:busy}} disabled={busy} value={a.included} onValueChange={v=>{setUndo({id:a.id,included:a.included});run(()=>imports.includeAsset(a.id,v));}}/></View>
    <View style={s.row}>{button(c("review.move_earlier"),()=>run(()=>imports.moveAsset(a.id,-1)))}{button(c("review.move_later"),()=>run(()=>imports.moveAsset(a.id,1)))}</View>
    {button(c("review.remove_item"),()=>Alert.alert(c("review.remove_item"),c("review.remove_confirm"),[{text:c("export.cancel"),style:"cancel"},{text:c("action.remove"),style:"destructive",onPress:()=>run(()=>imports.removeAsset(a.id,"REMOVE_SOURCE"))}]))}
   </View>)}
   {undo&&button(c("review.undo"),()=>{const u=undo;setUndo(null);run(()=>imports.includeAsset(u.id,u.included));})}
   {button(c("review.add_more"),()=>pick("visual"))}{button(c("review.cta"),()=>leave("song"),n.included===0)}
  </>}
  {screen==="song"&&<><Text style={labelStyle}>{c("song.body")}</Text><Text style={labelStyle}>{c("song.before_picker")}</Text>{song&&<Text style={labelStyle}>{c("import.READY")} · ⁦{clock(song.durationUs)}⁩</Text>}{button(c("song.cta"),()=>pick("song"))}{song&&button(c("voices.title"),()=>leave("voices"))}</>}
  {screen==="voices"&&<>
   <Text style={labelStyle}>{c("voices.body")}</Text>
   {current&&<><Text style={labelStyle}>{c("voices.clip_position",{n:Math.min(clip+1,videos.length),total:videos.length,time:clock(current.durationUs)})}</Text>{song?<NativePreview style={s.nativeFrame} assetId={current.id} revision={JSON.stringify({id:p?.projectId,assets:p?.assets.map(a=>[a.id,a.included]),ranges:p?.voiceRanges})} command={command} onPreviewState={e=>setPreview({...e.nativeEvent,clipPositionUs:(e.nativeEvent.clipPositionUs??-1)<0?null:e.nativeEvent.clipPositionUs})} onProjectChange={()=>imports.loadProject().then(apply).catch(()=>setStatus("COPY_FAILED"))}/>:placeholder}
    <Text style={labelStyle} accessibilityLabel={preview.positionSpokenEn?c("playback.seek_label",{position:rtl?preview.positionSpokenHe!:preview.positionSpokenEn,duration:rtl?preview.durationSpokenHe!:preview.durationSpokenEn!}):undefined}>⁦{clock(preview.clipPositionUs??0)}⁩</Text>
    {button(c("playback.play"),()=>sendPreview("play"),!song)}
    {button(c("playback.pause"),()=>sendPreview("pause"),preview.state!=="READY")}{button(c("export.cancel"),()=>sendPreview("stop"))}
    <View style={s.row}>{button(c("playback.seek_backward"),()=>sendPreview("seek",{deltaMs:-5000}),preview.state!=="READY")}{button(c("playback.seek_forward"),()=>sendPreview("seek",{deltaMs:5000}),preview.state!=="READY")}</View>
    {button(c("voices.mark_start"),()=>sendPreview("start"),!preview.canMark||!!p?.pendingVoiceStart||!!preview.markBusy)}{button(c("voices.mark_end"),()=>sendPreview("end"),!preview.canEndMark||!p?.pendingVoiceStart||!!preview.markBusy)}
    {preview.state!=="READY"&&<Text style={labelStyle}>{c("voices.playback_required")}</Text>}
    {preview.state==="UNSUPPORTED_MEDIA"&&<Text style={labelStyle}>{c("import.UNSUPPORTED_MEDIA")}</Text>}
    {preview.state==="PREVIEW_INTERRUPTED"&&<Text style={labelStyle}>{c("playback.interrupted")}</Text>}
    {preview.markBusy&&<Text accessibilityLiveRegion="polite" style={labelStyle}>{c("voices.mark_busy")}</Text>}
    {(preview.markFailed||preview.state==="PREVIEW_MARK_FAILED")&&<><Text style={labelStyle}>{c("voices.mark_failed")}</Text>{button(c("voices.mark_retry"),()=>sendPreview("retry"),!!preview.markBusy||!preview.markFailed)}</>}
    {preview.state==="PREVIEW_FAILED"&&<Text style={labelStyle}>{c("playback.unavailable")}</Text>}
    {button(c("voices.next_clip"),()=>leaveDecision(p!)==="finish-or-discard"?leave("voices"):setClip((clip+1)%videos.length),videos.length<2)}
   </>}
   {p?.pendingVoiceStart&&<><Text style={labelStyle}>{c("voices.pending_mark")}</Text>{button(c("voices.pending_finish"),()=>sendPreview("end"),!preview.canEndMark)}{button(c("voices.pending_discard"),()=>editStoredMark("discard"))}</>}
   {p?.voiceRanges.map((m,i)=><View key={m.id} style={s.card}><Text style={labelStyle}>{i+1} · ⁦{clock(m.startUs)} – {clock(m.endUs)}⁩</Text>{button(c("voices.remove_mark"),()=>editStoredMark("remove",m.id))}</View>)}
   <Text style={labelStyle}>{c(voiceHelperKey(p?.voiceRanges??[]))}</Text>
   {button(c(voiceContinueKey(p?.voiceRanges??[])),()=>p&&leaveDecision(p)==="finish-or-discard"?leave("plan"):check())}
  </>}
  {screen==="plan"&&<>
   <Text style={labelStyle}>{c((p?.voiceRanges.length??0)>0?"export.READY_TO_CREATE.style":"export.READY_TO_CREATE.style_nomarks")}</Text>
   {plan?.musicEndsAtMovieEnd&&<Text style={labelStyle}>{c("export.READY_TO_CREATE.song_long")}</Text>}
   <Text style={labelStyle}>{c("export.READY_TO_CREATE.foreground")}</Text>
   {job.active?<><Text accessibilityLiveRegion="polite" style={labelStyle}>{c(job.state==="PLANNING"?"export.PLANNING":job.state==="CANCEL_REQUESTED"?"export.CANCEL_REQUESTED":"export.EXPORTING",{percent:job.progressPercent==null?"":`${job.progressPercent.toLocaleString(lang)}%`})}</Text>
    {job.progressPercent!=null&&<Text style={labelStyle}>{job.progressPercent.toLocaleString(lang)}%</Text>}
    <Pressable accessibilityRole="button" accessibilityLabel={c("export.cancel")} style={s.button} onPress={()=>imports.cancelExport()}><Text style={labelStyle}>{c("export.cancel")}</Text></Pressable></>:
    <>{["EXPORT_CANCELLED","EXPORT_INTERRUPTED","EXPORT_FAILED","STORAGE_LOW"].includes(job.state)&&<Text style={labelStyle}>{c(("export."+job.state) as CopyKey)}</Text>}
    {button(c(["EXPORT_CANCELLED","EXPORT_INTERRUPTED","EXPORT_FAILED","STORAGE_LOW"].includes(job.state)?"export.restart":"export.READY_TO_CREATE.cta"),async()=>{if(!plan?.ok||!plan.projectId||!plan.planToken)return;const r=await imports.createExport(plan.projectId,plan.planToken);if(!r.ok)setStatus("COPY_FAILED");},!canCreateNative(plan,job,p?.projectId,filePlayerActive||!!delivery?.active||deliveryActionBusy))}</>}
   {job.reasonCode==="UNSUPPORTED_MEDIA"&&<Text style={labelStyle}>{c("import.UNSUPPORTED_MEDIA")}</Text>}
   {job.lastGood&&<View style={s.card}><Text style={labelStyle}>{c(job.state==="EXPORT_READY"?"export.EXPORT_READY.title":"export.previous_kept")}</Text>{delivery?.saveState==="SAVED"?<Text style={labelStyle}>{c("save.SAVED").replace("Movies › EventClip","\u2066Movies › EventClip\u2069")}</Text>:!(["SAVING","SAVE_FAILED"].includes(delivery?.saveState??""))&&<Text style={labelStyle}>{c("export.EXPORT_READY.private_note")}</Text>}
    <Text style={labelStyle}>MP4 · 720p · {new Intl.NumberFormat(lang,{style:"unit",unit:"byte",unitDisplay:"long"}).format(job.lastGood.bytes)}</Text>
    <NativePreview style={s.nativeFrame} assetId="" exportId={job.lastGood.exportId} revision={job.lastGood.exportId} command={command} onPreviewState={e=>{setPreview(e.nativeEvent);setFilePlayerActive(["PREPARING","READY","PREVIEW_ENDED"].includes(e.nativeEvent.state));}} onProjectChange={()=>{}}/>
    {button(c("playback.play"),()=>sendPreview("play"))}{button(c("playback.pause"),()=>sendPreview("pause"),preview.state!=="READY")}{button(c("export.cancel"),()=>sendPreview("stop"))}
    {preview.state==="PREVIEW_FAILED"&&<Text style={labelStyle}>{c("playback.unavailable")}</Text>}
    {delivery?.saveState==="SAVING"&&<Text accessibilityLiveRegion="polite" style={labelStyle}>{c(delivery.reasonCode==="CANCEL_REQUESTED"?"save.cancel_requested":"save.SAVING")}</Text>}
    {delivery?.saveState==="SAVE_FAILED"&&!delivery.cleanupPending&&<Text accessibilityLiveRegion="polite" style={labelStyle}>{c(delivery.reasonCode==="SAVE_CANCELLED"?"save.cancelled":"save.SAVE_FAILED")}</Text>}
    {delivery?.reasonCode==="STORAGE_LOW"&&<Text style={labelStyle}>{c("export.STORAGE_LOW")}</Text>}
    {button(c("export.EXPORT_READY.save"),()=>save(),delivery?.saveSupported===false||delivery?.saveState==="SAVED"||!!delivery?.cleanupPending)}
    {delivery?.active&&<Pressable accessibilityRole="button" accessibilityLabel={c("export.cancel")} style={s.button} onPress={()=>imports.cancelSave()}><Text style={labelStyle}>{c("export.cancel")}</Text></Pressable>}
    {delivery?.cleanupPending&&<Text style={labelStyle}>{c("save.status_unknown").replace("Movies › EventClip","\u2066Movies › EventClip\u2069")}</Text>}
    {delivery?.saveSupported===false&&<Text style={labelStyle}>{c("save.unsupported")}</Text>}
    {shareOpening&&<Text accessibilityLiveRegion="polite" style={labelStyle}>{c("share.busy")}</Text>}
    {shareFailed&&<Text accessibilityLiveRegion="polite" style={labelStyle}>{c("share.failed")}</Text>}
    {button(c("export.EXPORT_READY.share"),()=>share())}
    <Pressable accessibilityRole="button" accessibilityLabel={c("share.reminder_link")} style={s.button} onPress={()=>setPanel("reminder")}><Text style={labelStyle}>{c("share.reminder_link")}</Text></Pressable>
    {panel!=="none"&&<View style={s.card} onLayout={presented}><Text style={labelStyle}>{c("share.info_panel.title")}</Text><Text style={labelStyle}>{c("share.info_panel.body")}</Text>
     {panel==="first-share"&&button(c("share.notice.continue"),()=>share(true))}
     <Pressable accessibilityRole="button" accessibilityLabel={c("share.info_panel.dismiss")} style={s.button} onPress={()=>{presented();setPanel("none");}}><Text style={labelStyle}>{c("share.info_panel.dismiss")}</Text></Pressable>
    </View>}
   </View>}
   {button(c("export.EXPORT_READY.edit"),()=>leave("review"))}
  </>}
  {screen!=="start"&&button(c("start.title"),()=>leave("start"))}
 </ScrollView>;
}
const s=StyleSheet.create({page:{padding:20,paddingTop:48,gap:14},title:{fontSize:24,fontWeight:"700"},button:{minHeight:48,padding:14,backgroundColor:"#e7ecff",justifyContent:"center",borderRadius:8},disabled:{opacity:.5},row:{flexDirection:"row",flexWrap:"wrap",alignItems:"center",gap:12},card:{padding:14,borderWidth:1,borderColor:"#c7c7c7",borderRadius:8,gap:10},nativeFrame:{width:"100%",aspectRatio:9/16,backgroundColor:"#111",direction:"ltr"},frame:{aspectRatio:16/9,backgroundColor:"#111",padding:20,justifyContent:"center",alignItems:"center",direction:"ltr"},frameText:{color:"#fff",textAlign:"center"}});
