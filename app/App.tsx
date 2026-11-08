import { useEffect, useState } from "react";
import { StyleSheet, Text, View, Pressable, ScrollView, Platform, StatusBar } from "react-native";
import { getEngineInfo, runAllTests, run1080Once, listOutputs, shareOutput, cancelTests, addTestProgressListener } from "./modules/eventclip-render/src";
type Output = { available?: boolean; bytes?: number; source?: string; producerVersionCode?: number; runId?: string };
export default function App() {
  const [log,setLog]=useState("Synthetic offline diagnostic only. Foreground; no personal media/cloud.\n");
  const [busy,setBusy]=useState(false);
  const [status,setStatus]=useState("Idle");
  const [outputs,setOutputs]=useState<Record<string,Output>>({});
  const [attempted1080,setAttempted1080]=useState(false);
  const append=(line:string)=>setLog(p=>p+line+"\n");
  const refresh=async()=>{setOutputs(await listOutputs() as Record<string,Output>);};
  useEffect(()=>{
    const sub=addTestProgressListener(ev=>{
      const ss=(ev as any)?.scenarios;
      const keys=ss?Object.keys(ss):[];
      const last=keys[keys.length-1];
      setStatus(last?`${last}: ${ss[last]?.status ?? "?"}`:String((ev as any)?.fixtureProgress??"Preparing"));
    });
    (async()=>{try {await getEngineInfo();await refresh();}catch(e:any){append("Init: "+String(e?.message??e));}})();
    return ()=>sub?.remove();
  },[]);
  const run=async(focused:boolean)=>{
    if(busy)return;
    setBusy(true);
    if(focused)setAttempted1080(true);
    setStatus("Running");
    try {
      const r=focused?await run1080Once():await runAllTests();
      append(JSON.stringify(r));
      setStatus((r as any).cancelled?"Cancelled — previous outputs retained":"Finished — inspect result status");
    }catch(e:any){setStatus("Error — previous outputs retained");append(String(e?.message??e));}
    finally {setBusy(false);try{await refresh();}catch(e:any){append(String(e?.message??e));}}
  };
  const share=async(id:"json"|"720"|"1080")=>{
    try {append(JSON.stringify(await shareOutput(id)));}catch(e:any){append("Share: "+String(e?.message??e));}
  };
  const prereqs=outputs.matched20?.available && outputs["720"]?.available;
  return <View style={styles.container}>
    <Text style={styles.title}>EventClip v5 diagnostic</Text>
    <Text style={styles.sub}>Foreground full1080 · watchdog 21000ms · synthetic pack4</Text>
    <Text style={styles.sub}>Status: {status}</Text>
    <Text style={styles.caveat}>After agreement via Owner: keep app foreground, tap Run 1080 once, then Share JSON and the individual MP4s. No automatic retries. Existing successful files are retained.</Text>
    <Text style={styles.caveat}>1080 requires stored valid matched20s + 720. If missing, use Run matched20s + 720 first. Full workload: 60 images×4.7s +15 duplicate clips×1.2s =300s. No bitrate/resolution size cap.</Text>
    <View style={styles.row}>
      <Pressable style={[styles.btn,styles.primary]} disabled={busy||!prereqs||attempted1080} onPress={()=>run(true)}><Text style={styles.btnText}>Run 1080 once</Text></Pressable>
      <Pressable style={styles.btn} disabled={busy} onPress={()=>run(false)}><Text style={styles.btnText}>Run matched20s + 720</Text></Pressable>
      <Pressable style={styles.btn} disabled={!busy} onPress={async()=>{await cancelTests();append("Cancel requested");}}><Text style={styles.btnText}>Cancel</Text></Pressable>
    </View>
    {(["720","1080"] as const).map(id=><Text key={id} style={styles.sub}>{id}: {outputs[id]?.available?`${outputs[id].bytes} bytes (${((outputs[id].bytes??0)/1048576).toFixed(2)} MiB); ${outputs[id].source?.startsWith("imported_vc4")?"previous vc4 run; hash established on import":"retained successful run"}; ${outputs[id].runId}`:"No validated output available"}</Text>)}
    <Text style={styles.caveat}>1080 size unknown until export; may exceed 25MiB recipient attachment limits. Share/save one file at a time via Android chooser. Files are not sent automatically. Audit metadata is not playback/decode verification.</Text>
    <View style={styles.row}>
      <Pressable style={styles.btn} disabled={busy} onPress={()=>share("json")}><Text style={styles.btnText}>Share JSON</Text></Pressable>
      <Pressable style={styles.btn} disabled={busy||!outputs["720"]?.available} onPress={()=>share("720")}><Text style={styles.btnText}>Share MP4 720</Text></Pressable>
      <Pressable style={styles.btn} disabled={busy||!outputs["1080"]?.available} onPress={()=>share("1080")}><Text style={styles.btnText}>Share MP4 1080</Text></Pressable>
    </View>
    <ScrollView style={styles.log}><Text style={styles.logText}>{log}</Text></ScrollView>
    <StatusBar barStyle="light-content"/>
  </View>;
}
const styles=StyleSheet.create({
 container:{flex:1,backgroundColor:"#0b1220",paddingTop:48,paddingHorizontal:12},title:{color:"#fff",fontSize:22,fontWeight:"700"},sub:{color:"#9fb0c7",marginTop:4,fontSize:12},caveat:{color:"#fbbf24",marginTop:8,fontSize:11},row:{flexDirection:"row",flexWrap:"wrap",gap:8,marginTop:12},btn:{backgroundColor:"#334155",paddingVertical:12,paddingHorizontal:14,borderRadius:10},primary:{backgroundColor:"#2563eb"},btnText:{color:"#fff",fontWeight:"700"},log:{marginTop:12,backgroundColor:"#111827",borderRadius:10,padding:10,flex:1},logText:{color:"#d1d5db",fontFamily:Platform.OS==="android"?"monospace":"Courier",fontSize:11}
});
