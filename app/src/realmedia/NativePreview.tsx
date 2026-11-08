import {requireNativeViewManager} from "expo-modules-core";
import type {ViewProps} from "react-native";
export type PreviewState={state:string;positionUs:number;durationUs:number;clipPositionUs:number|null;canMark:boolean;canEndMark:boolean;positionSpokenEn?:string;positionSpokenHe?:string;durationSpokenEn?:string;durationSpokenHe?:string;markBusy?:boolean;markFailed?:boolean};
export type PreviewCommand={action:string;nonce:number;deltaMs?:number;rangeId?:string};
type Props=ViewProps&{assetId:string;exportId?:string;revision:string;command:PreviewCommand;onPreviewState:(event:{nativeEvent:PreviewState})=>void;onProjectChange:()=>void};
export const NativePreview=requireNativeViewManager<Props>("BirthdayImports");
