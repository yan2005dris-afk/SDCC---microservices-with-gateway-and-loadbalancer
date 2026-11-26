import React from "react";
import { interpolate, spring, useCurrentFrame, useVideoConfig } from "remotion";

export const SceneAllisonTransactions: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const titleIn = spring({ frame, fps, config: { damping: 14, stiffness: 90 } });
  const contentIn = spring({ frame: frame - 20, fps, config: { damping: 14, stiffness: 85 } });

  // Animación del flujo de creación de pedido
  // Fase 1 (0-60 frames): POST /api/orders llega a order-service
  // Fase 2 (60-120 frames): RPC call a inventory-service /reserve
  // Fase 3 (120-180 frames): Commit en postgres-primary (orders_db e inventory_db)
  const cycleFrame = frame % 180;
  const isPhase1 = cycleFrame < 60;
  const isPhase2 = cycleFrame >= 60 && cycleFrame < 120;
  const isPhase3 = cycleFrame >= 120;

  return (
    <div
      style={{
        flex: 1,
        backgroundColor: "#080c14",
        backgroundImage: "radial-gradient(ellipse at 50% 30%, #311042 0%, #080c14 75%)",
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
            background: "rgba(236, 72, 153, 0.15)",
            border: "1px solid rgba(236, 72, 153, 0.4)",
            borderRadius: "9999px",
            padding: "8px 20px",
            fontSize: "18px",
            color: "#f472b6",
            fontWeight: 700,
            textTransform: "uppercase",
            marginBottom: "12px",
          }}
        >
          Etapa 3 · Allison Reyes · Pruebas P04 y P05
        </div>
        <h1
          style={{
            fontSize: "48px",
            fontWeight: 900,
            margin: 0,
            background: "linear-gradient(to right, #ffffff, #f472b6, #db2777)",
            WebkitBackgroundClip: "text",
            WebkitTextFillColor: "transparent",
          }}
        >
          Transacciones Distribuidas y Reserva de Stock
        </h1>
        <p style={{ color: "#94a3b8", fontSize: "20px", marginTop: "8px" }}>
          Comunicación RPC Inter-servicio y Enrutamiento a <code>PostgreSQL PRIMARY</code>
        </p>
      </div>

      {/* Flujo animado de microservicios */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          gap: "40px",
          width: "100%",
          maxWidth: "1400px",
          opacity: contentIn,
        }}
      >
        {/* Colección Bruno / Cliente */}
        <div
          style={{
            background: isPhase1 ? "rgba(244, 114, 182, 0.2)" : "#1e293b",
            border: `2px solid ${isPhase1 ? "#f472b6" : "#475569"}`,
            borderRadius: "16px",
            padding: "20px",
            width: "240px",
            textAlign: "center",
            boxShadow: isPhase1 ? "0 0 25px rgba(244, 114, 182, 0.3)" : "none",
          }}
        >
          <div style={{ fontSize: "32px", marginBottom: "6px" }}>🧪</div>
          <div style={{ fontWeight: 800, fontSize: "18px" }}>Bruno Collection</div>
          <div style={{ color: "#f472b6", fontSize: "13px", marginTop: "6px" }}>
            <code>POST /api/orders</code>
          </div>
          <div style={{ color: "#94a3b8", fontSize: "12px", marginTop: "4px" }}>
            Qty: 2 · PROD-001
          </div>
        </div>

        <div style={{ fontSize: "28px", color: isPhase1 ? "#f472b6" : "#475569" }}>➔</div>

        {/* Order Service */}
        <div
          style={{
            background: isPhase1 || isPhase2 ? "rgba(56, 189, 248, 0.2)" : "#0f172a",
            border: `2px solid ${isPhase1 || isPhase2 ? "#38bdf8" : "#334155"}`,
            borderRadius: "16px",
            padding: "24px",
            width: "270px",
            textAlign: "center",
            boxShadow: isPhase1 || isPhase2 ? "0 0 25px rgba(56, 189, 248, 0.3)" : "none",
          }}
        >
          <div style={{ fontSize: "32px", marginBottom: "6px" }}>📦</div>
          <div style={{ fontWeight: 800, fontSize: "20px", color: "#38bdf8" }}>Order Service</div>
          <div style={{ color: "#94a3b8", fontSize: "13px", marginTop: "6px" }}>
            Valida DTO y coordina
          </div>
          {isPhase2 && (
            <div style={{ color: "#38bdf8", fontSize: "12px", fontWeight: 700, marginTop: "8px" }}>
              Llamando a Inventario...
            </div>
          )}
        </div>

        <div style={{ fontSize: "28px", color: isPhase2 ? "#a855f7" : "#475569" }}>➔</div>

        {/* Inventory Service */}
        <div
          style={{
            background: isPhase2 ? "rgba(168, 85, 247, 0.2)" : "#0f172a",
            border: `2px solid ${isPhase2 ? "#a855f7" : "#334155"}`,
            borderRadius: "16px",
            padding: "24px",
            width: "270px",
            textAlign: "center",
            boxShadow: isPhase2 ? "0 0 25px rgba(168, 85, 247, 0.3)" : "none",
          }}
        >
          <div style={{ fontSize: "32px", marginBottom: "6px" }}>🏷️</div>
          <div style={{ fontWeight: 800, fontSize: "20px", color: "#a855f7" }}>Inventory Service</div>
          <div style={{ color: "#94a3b8", fontSize: "13px", marginTop: "6px" }}>
            <code>/api/inventory/reserve</code>
          </div>
          {isPhase2 && (
            <div style={{ color: "#34d399", fontSize: "12px", fontWeight: 700, marginTop: "8px" }}>
              Stock: 50 → 48 (Reservado)
            </div>
          )}
        </div>

        <div style={{ fontSize: "28px", color: isPhase3 ? "#10b981" : "#475569" }}>➔</div>

        {/* PostgreSQL Primary */}
        <div
          style={{
            background: isPhase3 ? "rgba(16, 185, 129, 0.2)" : "#0f172a",
            border: `2px solid ${isPhase3 ? "#10b981" : "#334155"}`,
            borderRadius: "16px",
            padding: "24px",
            width: "270px",
            textAlign: "center",
            boxShadow: isPhase3 ? "0 0 25px rgba(16, 185, 129, 0.3)" : "none",
          }}
        >
          <div style={{ fontSize: "32px", marginBottom: "6px" }}>🐘</div>
          <div style={{ fontWeight: 800, fontSize: "20px", color: "#34d399" }}>postgres-primary</div>
          <div style={{ color: "#94a3b8", fontSize: "13px", marginTop: "6px" }}>
            Escritura en <code>orders_db</code>
          </div>
          <div style={{ color: "#34d399", fontSize: "12px", fontWeight: 700, marginTop: "8px" }}>
            Header: <code>X-Database-Node: PRIMARY</code>
          </div>
        </div>
      </div>

      {/* Casos Evaluados */}
      <div
        style={{
          display: "flex",
          gap: "24px",
          width: "100%",
          maxWidth: "1200px",
          opacity: contentIn,
        }}
      >
        <div
          style={{
            flex: 1,
            background: "rgba(15, 23, 42, 0.8)",
            border: "1px solid rgba(244, 114, 182, 0.3)",
            borderRadius: "14px",
            padding: "16px 20px",
          }}
        >
          <div style={{ color: "#f472b6", fontWeight: 700, fontSize: "16px" }}>
            P04: Caso Aceptado vs Stock Insuficiente
          </div>
          <div style={{ color: "#94a3b8", fontSize: "14px", marginTop: "4px" }}>
            Pedido con 2 unidades responde 201 Created. Petición con 999 unidades responde 400 Bad Request y no genera pedidos huérfanos.
          </div>
        </div>
        <div
          style={{
            flex: 1,
            background: "rgba(15, 23, 42, 0.8)",
            border: "1px solid rgba(52, 211, 153, 0.3)",
            borderRadius: "14px",
            padding: "16px 20px",
          }}
        >
          <div style={{ color: "#34d399", fontWeight: 700, fontSize: "16px" }}>
            P05: Lectura Crítica (Read-After-Write)
          </div>
          <div style={{ color: "#94a3b8", fontSize: "14px", marginTop: "4px" }}>
            El endpoint <code>GET /api/orders/&#123;id&#125;</code> con header <code>X-Consistency: critical</code> fuerza lectura en Primary para evitar leer retraso de replicación.
          </div>
        </div>
      </div>
    </div>
  );
};
