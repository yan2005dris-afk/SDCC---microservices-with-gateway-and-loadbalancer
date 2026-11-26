import React from "react";
import { interpolate, spring, useCurrentFrame, useVideoConfig } from "remotion";

export const SceneAndyFailover: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const titleIn = spring({ frame, fps, config: { damping: 14, stiffness: 90 } });
  const contentIn = spring({ frame: frame - 20, fps, config: { damping: 14, stiffness: 85 } });

  // Simulación:
  // 0 - 90 frames: Caída de order-service-1 (P03)
  // 90 - 180 frames: Caída de postgres-replica1 y conmutación a replica2 (P06 / P08)
  const isSecondScenario = frame >= 90;

  return (
    <div
      style={{
        flex: 1,
        backgroundColor: "#080c14",
        backgroundImage: "radial-gradient(ellipse at 50% 30%, #450a0a 0%, #080c14 75%)",
        color: "#ffffff",
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        alignItems: "center",
        fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, sans-serif",
        padding: "60px 80px",
      }}
    >
      {/* Header */}
      <div style={{ textAlign: "center", opacity: titleIn }}>
        <div
          style={{
            display: "inline-block",
            background: "rgba(239, 68, 68, 0.15)",
            border: "1px solid rgba(239, 68, 68, 0.4)",
            borderRadius: "9999px",
            padding: "8px 20px",
            fontSize: "18px",
            color: "#f87171",
            fontWeight: 700,
            textTransform: "uppercase",
            marginBottom: "12px",
          }}
        >
          Etapa 4 · Andy Alejandro · Pruebas P03, P06 y P08
        </div>
        <h1
          style={{
            fontSize: "48px",
            fontWeight: 900,
            margin: 0,
            background: "linear-gradient(to right, #ffffff, #fca5a5, #ef4444)",
            WebkitBackgroundClip: "text",
            WebkitTextFillColor: "transparent",
          }}
        >
          Resiliencia y Conmutación por Fallo (Failover)
        </h1>
        <p style={{ color: "#94a3b8", fontSize: "20px", marginTop: "8px" }}>
          Inyección de fallos en contenedores y recuperación sin pérdida de servicio
        </p>
      </div>

      {/* Escenarios Interactivos */}
      <div
        style={{
          display: "flex",
          gap: "40px",
          width: "100%",
          maxWidth: "1400px",
          opacity: contentIn,
          justifyContent: "center",
        }}
      >
        {/* Caso A: Fallo en Instancia de Microservicio */}
        <div
          style={{
            flex: 1,
            background: !isSecondScenario ? "rgba(30, 41, 59, 0.9)" : "rgba(15, 23, 42, 0.5)",
            border: `2px solid ${!isSecondScenario ? "#ef4444" : "#334155"}`,
            borderRadius: "20px",
            padding: "30px",
            boxShadow: !isSecondScenario ? "0 0 30px rgba(239, 68, 68, 0.25)" : "none",
          }}
        >
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <h3 style={{ margin: 0, fontSize: "22px", color: "#f87171" }}>
              P03 · Fallo de Microservicio
            </h3>
            <span
              style={{
                background: "#991b1b",
                color: "#fee2e2",
                padding: "4px 10px",
                borderRadius: "6px",
                fontSize: "12px",
                fontWeight: 700,
              }}
            >
              docker stop order-service-1
            </span>
          </div>
          <div style={{ marginTop: "20px", display: "flex", flexDirection: "column", gap: "14px" }}>
            <div
              style={{
                background: "rgba(239, 68, 68, 0.2)",
                border: "1px dashed #ef4444",
                borderRadius: "10px",
                padding: "12px 16px",
                color: "#fca5a5",
                display: "flex",
                justifyContent: "space-between",
              }}
            >
              <span>order-service-1 (:8081)</span>
              <strong style={{ color: "#ef4444" }}>✕ CAÍDO (STOPPED)</strong>
            </div>
            <div
              style={{
                background: "rgba(16, 185, 129, 0.15)",
                border: "1px solid #10b981",
                borderRadius: "10px",
                padding: "12px 16px",
                color: "#34d399",
                display: "flex",
                justifyContent: "space-between",
              }}
            >
              <span>order-service-2 (:8083)</span>
              <strong>✓ 100% TRÁFICO ACTIVO</strong>
            </div>
          </div>
          <p style={{ color: "#94a3b8", fontSize: "14px", marginTop: "16px", lineHeight: 1.5 }}>
            Spring Cloud Gateway detecta la desconexión o caída en Eureka y redirige el 100% de los requests a la instancia 2 sin degradación visible para el cliente.
          </p>
        </div>

        {/* Caso B: Fallo en Réplica de Lectura */}
        <div
          style={{
            flex: 1,
            background: isSecondScenario ? "rgba(30, 41, 59, 0.9)" : "rgba(15, 23, 42, 0.5)",
            border: `2px solid ${isSecondScenario ? "#f59e0b" : "#334155"}`,
            borderRadius: "20px",
            padding: "30px",
            boxShadow: isSecondScenario ? "0 0 30px rgba(245, 158, 11, 0.25)" : "none",
          }}
        >
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <h3 style={{ margin: 0, fontSize: "22px", color: "#fbbf24" }}>
              P06 & P08 · Fallo y Reincorporación BD
            </h3>
            <span
              style={{
                background: "#78350f",
                color: "#fef3c7",
                padding: "4px 10px",
                borderRadius: "6px",
                fontSize: "12px",
                fontWeight: 700,
              }}
            >
              docker stop postgres-replica1
            </span>
          </div>
          <div style={{ marginTop: "20px", display: "flex", flexDirection: "column", gap: "14px" }}>
            <div
              style={{
                background: "rgba(239, 68, 68, 0.2)",
                border: "1px dashed #ef4444",
                borderRadius: "10px",
                padding: "12px 16px",
                color: "#fca5a5",
                display: "flex",
                justifyContent: "space-between",
              }}
            >
              <span>postgres-replica1</span>
              <strong style={{ color: "#ef4444" }}>✕ CAÍDO (STOPPED)</strong>
            </div>
            <div
              style={{
                background: "rgba(16, 185, 129, 0.15)",
                border: "1px solid #10b981",
                borderRadius: "10px",
                padding: "12px 16px",
                color: "#34d399",
                display: "flex",
                justifyContent: "space-between",
              }}
            >
              <span>postgres-replica2</span>
              <strong>✓ ABSORBE LECTURAS (REPLICA_2)</strong>
            </div>
          </div>
          <p style={{ color: "#94a3b8", fontSize: "14px", marginTop: "16px", lineHeight: 1.5 }}>
            <code>AbstractRoutingDataSource</code> detecta que réplica 1 no responde y conmuta automáticamente a réplica 2. Al levantar réplica 1, se reconecta vía WAL streaming sin reiniciar microservicios.
          </p>
        </div>
      </div>

      {/* Footer de verificación */}
      <div
        style={{
          background: "rgba(15, 23, 42, 0.8)",
          border: "1px solid rgba(255,255,255,0.1)",
          borderRadius: "14px",
          padding: "16px 28px",
          width: "100%",
          maxWidth: "1200px",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          opacity: contentIn,
        }}
      >
        <span style={{ color: "#cbd5e1", fontSize: "16px" }}>
          Verificación WAL en vivo: <code>SELECT application_name, state, sync_state FROM pg_stat_replication;</code>
        </span>
        <span style={{ color: "#34d399", fontWeight: 700, fontSize: "15px" }}>
          ● Estado: STREAMING (Asíncrono)
        </span>
      </div>
    </div>
  );
};
