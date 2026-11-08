import type {Project,VoiceRange,PlanResult,ExportState,DeliveryState} from "./imports";
export type Screen="start"|"review"|"song"|"voices"|"plan";
export function voiceContinueKey(saved:readonly VoiceRange[]){return saved.length?"voices.continue_saved":"voices.skip_all";}
export function voiceHelperKey(saved:readonly VoiceRange[]){return saved.length?"voices.skip_all_helper_kept":"voices.skip_all_helper";}
export function leaveDecision(p:Pick<Project,"pendingVoiceStart">){return p.pendingVoiceStart?"finish-or-discard":"continue";}
export function counts(p:Project){const visual=p.assets.filter(a=>a.kind!=="song");return {retained:visual.length,included:visual.filter(a=>a.included).length,excluded:visual.filter(a=>!a.included).length};}
export function canCheckPlan(p:Project){return counts(p).included>0 && p.assets.filter(a=>a.kind!=="song"&&a.included).every(a=>a.state==="READY") && p.assets.some(a=>a.kind==="song" && a.state==="READY") && !p.pendingVoiceStart;}
export function secondsCeil(us:number){if(!Number.isSafeInteger(us)||us<0)throw Error("Invalid duration");return Math.ceil(us/1000000);}
export function clock(us:number){const seconds=secondsCeil(us);return `${Math.floor(seconds/60)}:${String(seconds%60).padStart(2,"0")}`;}
export function errorCopyKey(code:string):string {return code==="INCOMPLETE_VOICE_RANGE"?"voices.pending_mark":`import.${code}`;}
/** No playback clock: mark buttons MUST remain disabled, never create invented timestamps. */
export function canMark(playbackClockUs:number|null,durationUs:number){return playbackClockUs!==null && Number.isSafeInteger(playbackClockUs) && playbackClockUs>=0 && playbackClockUs<durationUs;}

export function displayAspect(width:number,height:number,rotation:number){if(width<=0||height<=0)return 16/9;return Math.abs(rotation)%180===90?height/width:width/height;}

export function canCreateNative(plan:PlanResult|null,job:ExportState,projectId?:string,playerActive=false){return !!(plan?.ok && plan.renderImplemented && plan.projectId===projectId && plan.planToken && !job.active && !playerActive);}
export function mergeExportState(previous:ExportState,next:ExportState):ExportState {
 if(next.state==="EXPORT_READY"&&(!next.lastGood||!next.lastGood.metadataVerified))return {...previous,state:"EXPORT_FAILED",active:false};
 return {...previous,...next};
}

export function automaticInformationPanel(info:DeliveryState){return info.infoShown===false;}
export function firstShareNeedsNotice(info:DeliveryState){return info.firstShareContinued!==true;}
export function mergeDeliveryState(previous:DeliveryState|null,next:DeliveryState,currentId:string):DeliveryState|null {if(next.exportId!==currentId)return previous;return {...(previous?.exportId===currentId?previous:{}),...next};}
