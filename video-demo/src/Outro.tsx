import { interpolate, spring, useCurrentFrame, useVideoConfig } from "remotion";
import React from "react";

export const Outro: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const titleEntrance = spring({
    frame,
    fps,
    config: { damping: 14, stiffness: 90 },
  });

  const cardsEntrance = spring({
    frame: frame - 25,
    fps,
    config: { damping: 14, stiffness: 85 },
  });

  const footerEntrance = spring({
    frame: frame - 60,
    fps,
    config: { damping: 12, stiffness: 100 },
  });

  const fadeOut = interpolate(frame, [210, 240], [1, 0], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  return (
    <div
      style={{
        flex: 1,
        backgroundColor: "#080c14",
        backgroundImage: "radial-gradient(ellipse at 50% 80%, #064e3b 0%, #080c14 70%)",
        color: "#ffffff",
        display: "flex",
        flexDirection: "column",
        justifyContent: "center",
        alignItems: "center",
        fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, sans-serif",
        padding: "60px",
        opacity: fadeOut,
      }}
    >
      {/* Título Conclusiones */}
      <h1
        style={{
          transform: `translateY(${interpolate(titleEntrance, [0, 1], [35, 0])}px)`,
          opacity: titleEntrance,
          fontSize: "58px",
          fontWeight: 900,
          textAlign: "center",
          margin: 0,
          background: "linear-gradient(to right, #34d399, #6ee7b7, #ffffff)",
          WebkitBackgroundClip: "text",
          WebkitTextFillColor: "transparent",
          letterSpacing: "-0.03em",
          lineHeight: 1.15,
        }}
      >
        Conclusiones y Verificación Experimental
      </h1>

      <p
        style={{
          transform: `translateY(${interpolate(titleEntrance, [0, 1], [20, 0])}px)`,
          opacity: titleEntrance,
          fontSize: "24px",
          color: "#94a3b8",
          marginTop: "14px",
          marginBottom: "46px",
          textAlign: "center",
        }}
      >
        Comportamiento en régimen estable y tolerancia a fallos
      </p>

      {/* Grid de Puntos Clave del Guion */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "1fr 1fr",
          gap: "24px",
          maxWidth: "1050px",
          width: "100%",
          transform: `scale(${cardsEntrance})`,
          opacity: cardsEntrance,
          marginBottom: "50px",
        }}
      >
        <div
          style={{
            background: "rgba(15, 23, 42, 0.8)",
            border: "1px solid rgba(52, 211, 153, 0.3)",
            borderRadius: "16px",
            padding: "24px",
          }}
        >
          <div style={{ color: "#34d399", fontWeight: 700, fontSize: "20px", marginBottom: "8px" }}>
            ✓ Tolerancia a Fallos en BD y Servicios
          </div>
          <div style={{ color: "#cbd5e1", fontSize: "17px", lineHeight: 1.5 }}>
            Al fallar una instancia o réplica, las consultas continúan sin interrumpir la operación global del cluster.
          </div>
        </div>

        <div
          style={{
            background: "rgba(15, 23, 42, 0.8)",
            border: "1px solid rgba(56, 189, 248, 0.3)",
            borderRadius: "16px",
            padding: "24px",
          }}
        >
          <div style={{ color: "#38bdf8", fontWeight: 700, fontSize: "20px", marginBottom: "8px" }}>
            ✓ Detección de Transición (503 → 200)
          </div>
          <div style={{ color: "#cbd5e1", fontSize: "17px", lineHeight: 1.5 }}>
            Durante el reinicio de ambas instancias de Pedidos, se evidencian respuestas 503 temporales antes de recuperar disponibilidad.
          </div>
        </div>

        <div
          style={{
            background: "rgba(15, 23, 42, 0.8)",
            border: "1px solid rgba(167, 139, 250, 0.3)",
            borderRadius: "16px",
            padding: "24px",
          }}
        >
          <div style={{ color: "#a78bfa", fontWeight: 700, fontSize: "20px", marginBottom: "8px" }}>
            ✓ Trazabilidad por Cabeceras
          </div>
          <div style={{ color: "#cbd5e1", fontSize: "17px", lineHeight: 1.5 }}>
            Validación real con <code>X-Instance-Id</code> y <code>X-Database-Node: REPLICA_1 / PRIMARY</code>.
          </div>
        </div>

        <div
          style={{
            background: "rgba(15, 23, 42, 0.8)",
            border: "1px solid rgba(251, 191, 36, 0.3)",
            borderRadius: "16px",
            padding: "24px",
          }}
        >
          <div style={{ color: "#fbbf24", fontWeight: 700, fontSize: "20px", marginBottom: "8px" }}>
            ✓ Cierre del Demostrativo
          </div>
          <div style={{ color: "#cbd5e1", fontSize: "17px", lineHeight: 1.5 }}>
            Equipo: Yandris Rivera, Gino Bermúdez, Allison Reyes y Andy Alejandro.
          </div>
        </div>
      </div>

      {/* Footer / GitHub */}
      <div
        style={{
          transform: `translateY(${interpolate(footerEntrance, [0, 1], [15, 0])}px)`,
          opacity: footerEntrance,
          fontSize: "20px",
          color: "#94a3b8",
          display: "flex",
          gap: "18px",
          alignItems: "center",
        }}
      >
        <span>Repositorio GitHub: <strong style={{ color: "#ffffff" }}>SDCC - Microservices with Gateway and Loadbalancer</strong></span>
      </div>
    </div>
  );
};
