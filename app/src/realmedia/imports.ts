import { requireNativeModule, EventEmitter, type EventSubscription } from "expo-modules-core";
export type Asset={id:string;kind:"image"|"video"|"song";bytes:number;durationMs:number;durationUs:number;width:number;height:number;rotation:number;included:boolean;state:string};
export type VoiceRange={id:string;assetId:string;startUs:number;endUs:number};
export type Project={projectId:string;assets:Asset[];keepDateOrder:boolean;totalSourceBytes:number;foundationOnly:true;voiceRanges:VoiceRange[];pendingVoiceStart:{assetId:string;startUs:number}|null};
export type Result={ok:boolean;code?:string;project?:Project;results?:{selectionIndex:number;code:string}[]};
export type PlanResult={ok:boolean;code?:string;durationUs?:number;displaySecondsCeil?:number;musicEndsAtMovieEnd?:boolean;renderImplemented?:boolean;projectId?:string;planToken?:string};
export type PrivateExport={exportId:string;durationUs:number;bytes:number;decodeVerified:false;metadataVerified:true;videoSamples:number;audioSamples:number};
export type ExportState={state:string;active:boolean;progressPercent?:number|null;outBytes?:number;lastGood?:PrivateExport|null;cleanupPending?:boolean;reasonCode?:string};
export type DeliveryState={exportId:string;bytes?:number;saveState:string;active:boolean;writtenBytes?:number;infoShown?:boolean;firstShareContinued?:boolean;saveSupported?:boolean;savedUri?:string;reasonCode?:string;cleanupPending?:boolean};
export type ShareResult={state:"NEEDS_NOTICE"|"CHOOSER_OPENED"|"SHARE_FAILED"|"BUSY";bytes?:number;recipientDeliveryVerified?:false};
type Native={
 getDeliveryState(id:string):Promise<DeliveryState>;informationPresented(id:string):Promise<DeliveryState>;saveExport(id:string):Promise<{ok:boolean}>;cancelSave():Promise<unknown>;shareExport(id:string,continueFirst:boolean):Promise<ShareResult>;
 getExportState():Promise<ExportState>;createExport(projectId:string,token:string):Promise<{ok:boolean;code?:string}>;cancelExport():Promise<unknown>;
 replaceProject(confirmation:string):Promise<Result>;loadProject():Promise<Result>;pickAndImport(kind:"visual"|"song"):Promise<Result>;cancelImport():Promise<unknown>;
 removeAsset(id:string,confirmation:string):Promise<Result>;includeAsset(id:string,included:boolean):Promise<Result>;moveAsset(id:string,delta:number):Promise<Result>;setDateOrder(enabled:boolean):Promise<Result>;deleteProject(confirmation:string):Promise<Result>;
 startVoiceMark(id:string,atUs:number):Promise<Result>;finishVoiceMark(atUs:number):Promise<Result>;discardPendingVoiceStart():Promise<Result>;removeVoiceRange(id:string):Promise<Result>;planProject(beats:number[]):Promise<PlanResult>;
};
export const imports=requireNativeModule<Native>("BirthdayImports");
type Events={onDeliveryState:(event:DeliveryState)=>void;onExportState:(event:ExportState)=>void;onImportState:(event:{selectionIndex:number;selectionTotal:number;state:string})=>void};
const events=new EventEmitter<Events>(imports as any);
export function listenImports(fn:Events["onImportState"]):EventSubscription{return events.addListener("onImportState",fn);}

export function listenExport(fn:Events["onExportState"]):EventSubscription{return events.addListener("onExportState",fn);}

export function listenDelivery(fn:Events["onDeliveryState"]):EventSubscription{return events.addListener("onDeliveryState",fn);}
