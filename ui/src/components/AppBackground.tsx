import type {BackgroundImage} from '../lib/model';
export function AppBackground({image,enabled}:{image?:BackgroundImage;enabled:boolean}){
  const custom=!!image&&enabled;
  return <div className="app-background" aria-hidden="true" data-image={String(custom)} data-source={custom?'custom':'none'}>
    <div className="background-photo" style={{backgroundImage:custom?`url("${image.url}")`:'none'}}/><div className="background-veil"/>
  </div>;
}
