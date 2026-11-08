import bundle from "./designer-strings.json";
export type Language="en"|"he";
export type CopyKey=keyof typeof bundle.strings;
/** Values isolated; no mutation of Designer copy. Missing placeholders fail closed. */
export function copy(key:CopyKey,language:Language,values:Record<string,string|number>={}):string {
 return bundle.strings[key][language].replace(/\{(\w+)\}/g,(_,name:string)=>{
  if(!(name in values))throw new Error("Missing copy placeholder");
  return key==="playback.seek_label"?String(values[name]):"\u2068"+String(values[name])+"\u2069";
 });
}
