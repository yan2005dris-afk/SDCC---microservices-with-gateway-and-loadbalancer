import { interpolate, spring, useCurrentFrame, useVideoConfig } from "remotion";
import React from "react";

export const Intro: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  // Animaciones elásticas tipo spring (Framer / Remotion style)
  const titleEntrance = spring({
    frame,
    fps,
    config: { damping: 14, stiffness: 90 },
  });

  const subtitleEntrance = spring({
    frame: frame - 25,
    fps,
    config: { damping: 14, stiffness: 90 },
  });

  const cardsEntrance = spring({
    frame: frame - 55,
    fps,
    config: { damping: 15, stiffness: 80 },
  });

  const badgeEntrance = spring({
    frame: frame - 85,
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
        backgroundImage: "radial-gradient(ellipse at 50% 20%, #172554 0%, #080c14 70%)",
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
      {/* Badge Superior */}
      <div
        style={{
          transform: `scale(${badgeEntrance})`,
          opacity: badgeEntrance,
          background: "rgba(59, 130, 246, 0.15)",
          border: "1px solid rgba(59, 130, 246, 0.4)",
          borderRadius: "9999px",
          padding: "10px 24px",
          fontSize: "20px",
          color: "#60a5fa",
          letterSpacing: "0.1em",
          textTransform: "uppercase",
          fontWeight: 700,
          marginBottom: "28px",
        }}
      >
        Sistemas Distribuidos y Computación Paralela
      </div>

      {/* Título Principal */}
      <h1
        style={{
          transform: `translateY(${interpolate(titleEntrance, [0, 1], [40, 0])}px)`,
          opacity: titleEntrance,
          fontSize: "64px",
          fontWeight: 900,
          textAlign: "center",
          margin: 0,
          background: "linear-gradient(to right, #ffffff, #93c5fd, #38bdf8)",
          WebkitBackgroundClip: "text",
          WebkitTextFillColor: "transparent",
          letterSpacing: "-0.03em",
          lineHeight: 1.15,
          maxWidth: "1200px",
        }}
      >
        Microservicios, Gateway y Balanceo de Carga
      </h1>

      {/* Subtítulo / Tema */}
      <p
        style={{
          transform: `translateY(${interpolate(subtitleEntrance, [0, 1], [25, 0])}px)`,
          opacity: subtitleEntrance,
          fontSize: "28px",
          color: "#94a3b8",
          marginTop: "20px",
          marginBottom: "48px",
          textAlign: "center",
          maxWidth: "900px",
        }}
      >
        Demostración Práctica · Resiliencia, Replicación y Enrutamiento Activo
      </p>

      {/* Píldoras de Componentes de Arquitectura */}
      <div
        style={{
          display: "flex",
          gap: "20px",
          transform: `translateY(${interpolate(cardsEntrance, [0, 1], [30, 0])}px)`,
          opacity: cardsEntrance,
          marginBottom: "50px",
        }}
      >
        {[
          { label: "Spring Cloud Gateway", port: ":8080", color: "#38bdf8" },
          { label: "Eureka Service Discovery", port: ":8761", color: "#34d399" },
          { label: "Order & Inventory Services", port: "2 Instancias UP", color: "#a78bfa" },
          { label: "PostgreSQL Primary + 2 Réplicas", port: "Failover Ready", color: "#f472b6" },
        ].map((item, idx) => (
          <div
            key={idx}
            style={{
              background: "rgba(15, 23, 42, 0.75)",
              border: "1px solid rgba(255, 255, 255, 0.12)",
              borderRadius: "16px",
              padding: "16px 24px",
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              boxShadow: "0 10px 25px -5px rgba(0, 0, 0, 0.4)",
            }}
          >
            <span style={{ fontSize: "20px", fontWeight: 600, color: "#f8fafc" }}>
              {item.label}
            </span>
            <span style={{ fontSize: "15px", color: item.color, marginTop: "6px", fontWeight: 500 }}>
              {item.port}
            </span>
          </div>
        ))}
      </div>

      {/* Presentador */}
      <div
        style={{
          transform: `translateY(${interpolate(badgeEntrance, [0, 1], [20, 0])}px)`,
          opacity: badgeEntrance,
          display: "flex",
          alignItems: "center",
          gap: "14px",
          borderTop: "1px solid rgba(255, 255, 255, 0.15)",
          paddingTop: "24px",
        }}
      >
        <div
          style={{
            width: "14px",
            height: "14px",
            borderRadius: "50%",
            backgroundColor: "#22c55e",
            boxShadow: "0 0 12px #22c55e",
          }}
        />
        <span style={{ fontSize: "22px", color: "#e2e8f0" }}>
          Apertura: <strong style={{ color: "#38bdf8" }}>Yandris Rivera Torres</strong>
        </span>
      </div>
    </div>
  );
};
