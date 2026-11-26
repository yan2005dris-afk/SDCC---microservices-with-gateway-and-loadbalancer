import React from "react";
import { Composition, registerRoot, Series } from "remotion";
import { Intro } from "./Intro";
import { Outro } from "./Outro";
import { SceneGinoLoadBalancer } from "./SceneGinoLoadBalancer";
import { SceneAllisonTransactions } from "./SceneAllisonTransactions";
import { SceneAndyFailover } from "./SceneAndyFailover";
import { VideoGinoFull } from "./VideoGinoFull";

export const FullVideoPresentation: React.FC = () => {
  return (
    <Series>
      {/* 1. Yandris: Arquitectura y Descubrimiento (8 segundos) */}
      <Series.Sequence durationInFrames={240}>
        <Intro />
      </Series.Sequence>

      {/* 2. Gino: Balanceo de Carga HTTP y Terminal P02 (30 segundos completos) */}
      <Series.Sequence durationInFrames={900}>
        <SceneGinoLoadBalancer />
      </Series.Sequence>

      {/* 3. Allison: Transacciones e Inventario (10 segundos) */}
      <Series.Sequence durationInFrames={304}>
        <SceneAllisonTransactions />
      </Series.Sequence>

      {/* 4. Andy: Tolerancia a Fallos y Failover (12 segundos) */}
      <Series.Sequence durationInFrames={356} trimBefore={4}>
        <SceneAndyFailover />
      </Series.Sequence>

      {/* 5. Yandris: Conclusiones Finales (8 segundos) */}
      <Series.Sequence durationInFrames={240}>
        <Outro />
      </Series.Sequence>
    </Series>
  );
};

export const RemotionVideo: React.FC = () => {
  return (
    <>
      {/* Video Completo Integrado (Total: ~68 segundos) */}
      <Composition
        id="FullPresentation"
        component={FullVideoPresentation}
        durationInFrames={2040}
        fps={30}
        width={1920}
        height={1080}
      />

      {/* Composiciones Individuales por Sección */}
      <Composition
        id="Intro"
        component={Intro}
        durationInFrames={240}
        fps={30}
        width={1920}
        height={1080}
      />

      <Composition
        id="GinoLoadBalancer"
        component={SceneGinoLoadBalancer}
        durationInFrames={900}
        fps={30}
        width={1920}
        height={1080}
      />

      {/* Video Dedicado al Audio Completo de Gino Bermúdez (1:29 = 2670 frames) */}
      <Composition
        id="GinoAudioCompleto"
        component={VideoGinoFull}
        durationInFrames={2670}
        fps={30}
        width={1920}
        height={1080}
      />

      <Composition
        id="AllisonTransactions"
        component={SceneAllisonTransactions}
        durationInFrames={240}
        fps={30}
        width={1920}
        height={1080}
      />

      <Composition
        id="AndyFailover"
        component={SceneAndyFailover}
        durationInFrames={240}
        fps={30}
        width={1920}
        height={1080}
      />

      <Composition
        id="Outro"
        component={Outro}
        durationInFrames={240}
        fps={30}
        width={1920}
        height={1080}
      />
    </>
  );
};

registerRoot(RemotionVideo);
