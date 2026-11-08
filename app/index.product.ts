import {registerRootComponent} from "expo";
import ProductApp from "./App.realmedia.foundation";
// Auditable product bundle marker; no diagnostic App import, no automatic resume/autoplay.
export const EVENTCLIP_PRODUCT_ENTRY = "EVENTCLIP_PRODUCT_VC6_PRIVATE_ENTRY";
registerRootComponent(ProductApp);
