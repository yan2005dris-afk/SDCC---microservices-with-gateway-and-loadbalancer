import React from "react";
import { interpolate, spring, useCurrentFrame, useVideoConfig } from "remotion";

export const SceneGinoLoadBalancer: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  // 900 frames = 30 segundos
  const titleIn = spring({ frame, fps, config: { damping: 14, stiffness: 90 } });

  // Cronograma de 3 Fases en la Terminal:
  // FASE 1 (frames 0 - 380 | ~12.5s): Ejecución de 8 GETs (Balanceo P02 y Réplicas)
  // FASE 2 (frames 380 - 640 | ~8.5s): Ejecución de 4 POSTs (Creación en Primary + Inventory)
  // FASE 3 (frames 640 - 900 | ~9.0s): Ejecución suite Bruno CLI (6/6 tests PASSED)
  const isPhase1GET = frame < 380;
  const isPhase2POST = frame >= 380 && frame < 640;
  const isPhase3Bruno = frame >= 640;

  // Cadencia de peticiones en consola (cada 35 frames entra una petición)
  // En FASE 1 (GETs): 0..7
  const getIndex = Math.min(7, Math.floor(frame / 35));
  // En FASE 2 (POSTs): 0..3
  const postIndex = isPhase2POST ? Math.min(3, Math.floor((frame - 380) / 45)) : -1;
  // En FASE 3 (Bruno): 0..5
  const brunoIndex = isPhase3Bruno ? Math.min(5, Math.floor((frame - 640) / 25)) : -1;

  const requestsDataGET = [
    { req: "1", meth: "GET", status: "200", inst: "order-service-1:8081", node: "REPLICA_1", targetInst: "ord1", targetDb: "rep1", targetInv: false },
    { req: "2", meth: "GET", status: "200", inst: "order-service-2:8083", node: "REPLICA_2", targetInst: "ord2", targetDb: "rep2", targetInv: false },
    { req: "3", meth: "GET", status: "200", inst: "order-service-1:8081", node: "REPLICA_2", targetInst: "ord1", targetDb: "rep2", targetInv: false },
    { req: "4", meth: "GET", status: "200", inst: "order-service-2:8083", node: "REPLICA_1", targetInst: "ord2", targetDb: "rep1", targetInv: false },
    { req: "5", meth: "GET", status: "200", inst: "order-service-1:8081", node: "REPLICA_1", targetInst: "ord1", targetDb: "rep1", targetInv: false },
    { req: "6", meth: "GET", status: "200", inst: "order-service-2:8083", node: "REPLICA_2", targetInst: "ord2", targetDb: "rep2", targetInv: false },
    { req: "7", meth: "GET", status: "200", inst: "order-service-1:8081", node: "REPLICA_2", targetInst: "ord1", targetDb: "rep2", targetInv: false },
    { req: "8", meth: "GET", status: "200", inst: "order-service-2:8083", node: "REPLICA_1", targetInst: "ord2", targetDb: "rep1", targetInv: false },
  ];

  const requestsDataPOST = [
    { req: "1", meth: "POST", status: "201", inst: "order-service-1:8081", node: "PRIMARY", targetInst: "ord1", targetDb: "primary", targetInv: true },
    { req: "2", meth: "POST", status: "201", inst: "order-service-2:8083", node: "PRIMARY", targetInst: "ord2", targetDb: "primary", targetInv: true },
    { req: "3", meth: "POST", status: "201", inst: "order-service-1:8081", node: "PRIMARY", targetInst: "ord1", targetDb: "primary", targetInv: true },
    { req: "4", meth: "POST", status: "201", inst: "order-service-2:8083", node: "PRIMARY", targetInst: "ord2", targetDb: "primary", targetInv: true },
  ];

  const brunoTests = [
    { name: "01-eureka-apps.bru", method: "GET", url: ":8761/eureka/apps", time: "16ms", status: "PASS", targetInst: "eureka", targetDb: "none", targetInv: false },
    { name: "02-crear-pedido.bru", method: "POST", url: ":8080/api/orders", time: "42ms", status: "PASS", targetInst: "ord1", targetDb: "primary", targetInv: true },
    { name: "03-listar-pedidos.bru", method: "GET", url: ":8080/api/orders", time: "18ms", status: "PASS", targetInst: "ord2", targetDb: "rep1", targetInv: false },
    { name: "04-lectura-critica.bru", method: "GET", url: ":8080/api/orders/{id}", time: "12ms", status: "PASS", targetInst: "ord1", targetDb: "primary", targetInv: false },
    { name: "05-stock-inventario.bru", method: "GET", url: ":8080/api/inventory/PROD-001", time: "14ms", status: "PASS", targetInst: "none", targetDb: "primary", targetInv: true },
    { name: "06-stock-insuficiente.bru", method: "POST", url: ":8080/api/orders (999 qty)", time: "22ms", status: "PASS", targetInst: "ord2", targetDb: "none", targetInv: true },
  ];

  // DETERMINAR QUÉ ELEMENTOS SE ILUMINAN SINCRONIZADAMENTE:
  let activeInst = "none";
  let activeDb = "none";
  let activeInv = false;
  let activeGateway = true;

  if (isPhase1GET) {
    const activeReq = requestsDataGET[getIndex];
    activeInst = activeReq.targetInst;
    activeDb = activeReq.targetDb;
    activeInv = false;
  } else if (isPhase2POST) {
    const activeReq = requestsDataPOST[postIndex >= 0 ? postIndex : 0];
    activeInst = activeReq.targetInst;
    activeDb = activeReq.targetDb; // 'primary'
    activeInv = true; // POST siempre llama a inventario para reservar stock
  } else if (isPhase3Bruno) {
    const activeTest = brunoTests[brunoIndex >= 0 ? brunoIndex : 0];
    activeInst = activeTest.targetInst;
    activeDb = activeTest.targetDb;
    activeInv = activeTest.targetInv;
  }

  return (
    <div
      style={{
        flex: 1,
        backgroundColor: "#070b12",
        backgroundImage: "radial-gradient(ellipse at 50% 15%, #0c4a6e 0%, #070b12 75%)",
        color: "#ffffff",
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        alignItems: "center",
        fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, sans-serif",
        padding: "26px 45px",
      }}
    >
      {/* Header */}
      <div style={{ textAlign: "center", opacity: titleIn }}>
        <div
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: "10px",
            background: "rgba(56, 189, 248, 0.15)",
            border: "1px solid rgba(56, 189, 248, 0.4)",
            borderRadius: "9999px",
            padding: "5px 16px",
            fontSize: "14px",
            color: "#38bdf8",
            fontWeight: 700,
            textTransform: "uppercase",
            marginBottom: "4px",
          }}
        >
          <span>🎙️ Explicación: Gino Bermúdez</span>
          <span>•</span>
          <span>Sincronización Total en Vivo: Bash ⇄ Arquitectura</span>
        </div>
        <h1
          style={{
            fontSize: "34px",
            fontWeight: 900,
            margin: 0,
            background: "linear-gradient(to right, #ffffff, #7dd3fc, #38bdf8)",
            WebkitBackgroundClip: "text",
            WebkitTextFillColor: "transparent",
            letterSpacing: "-0.02em",
          }}
        >
          Enrutamiento Gateway, Balanceo de Carga y Replicación PostgreSQL
        </h1>
        <p style={{ color: "#94a3b8", fontSize: "14px", marginTop: "3px", margin: 0 }}>
          Iluminación sincronizada: <strong>GET</strong> enruta a Réplicas 1 y 2 · <strong>POST</strong> enruta a Primary + Inventory
        </p>
      </div>

      {/* CUERPO PRINCIPAL: DUAL PANEL */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "1.05fr 1.35fr",
          gap: "24px",
          width: "100%",
          maxWidth: "1740px",
          height: "670px",
        }}
      >
        {/* PANEL IZQUIERDO: Arquitectura Archify Sincronizada con Inventory */}
        <div
          style={{
            background: "rgba(15, 23, 42, 0.85)",
            border: "1px solid rgba(56, 189, 248, 0.25)",
            borderRadius: "18px",
            padding: "18px",
            display: "flex",
            flexDirection: "column",
            justifyContent: "space-between",
            boxShadow: "0 20px 40px rgba(0,0,0,0.5)",
            backdropFilter: "blur(12px)",
          }}
        >
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", borderBottom: "1px solid rgba(255,255,255,0.08)", paddingBottom: "8px" }}>
            <span style={{ fontWeight: 800, fontSize: "14px", color: "#38bdf8", letterSpacing: "0.05em" }}>
              ARQUITECTURA DISTRIBUIDA (ARCHIFY)
            </span>
            <span style={{ fontSize: "11px", background: "rgba(16, 185, 129, 0.2)", color: "#34d399", padding: "2px 8px", borderRadius: "6px", fontWeight: 600 }}>
              Docker Network (sdcc-network)
            </span>
          </div>

          {/* Nodos Interconectados */}
          <div style={{ display: "flex", flexDirection: "column", gap: "12px", margin: "auto 0" }}>
            {/* Top: Cliente HTTP */}
            <div style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: "12px" }}>
              <div style={{ background: "#1e293b", border: "1px solid #475569", borderRadius: "10px", padding: "6px 14px", fontSize: "12px", fontWeight: 700, display: "flex", alignItems: "center", gap: "8px" }}>
                <span>🌐</span> HTTP Client (bash / Bruno / curl)
              </div>
              <span style={{ color: "#38bdf8", fontWeight: 800, fontSize: "13px" }}>➔ :8080</span>
            </div>

            {/* Gateway y Eureka */}
            <div style={{ display: "grid", gridTemplateColumns: "1.4fr 1fr", gap: "10px" }}>
              <div style={{ background: "rgba(2, 132, 199, 0.15)", border: "2px solid #0284c7", borderRadius: "12px", padding: "8px", textAlign: "center", boxShadow: activeGateway ? "0 0 15px rgba(2, 132, 199, 0.35)" : "none" }}>
                <div style={{ fontWeight: 800, color: "#38bdf8", fontSize: "13px" }}>⚡ Spring Cloud Gateway</div>
                <div style={{ fontSize: "10px", color: "#cbd5e1" }}>:8080 · LoadBalancer + Retry</div>
              </div>

              <div style={{ background: activeInst === "eureka" ? "rgba(16, 185, 129, 0.3)" : "rgba(16, 185, 129, 0.1)", border: `2px solid ${activeInst === "eureka" ? "#10b981" : "#059669"}`, borderRadius: "12px", padding: "8px", textAlign: "center", transition: "all 0.2s" }}>
                <div style={{ fontWeight: 800, color: "#34d399", fontSize: "13px" }}>🧭 Eureka Server</div>
                <div style={{ fontSize: "10px", color: "#34d399", fontWeight: 700 }}>:8761 · Discovery UP</div>
              </div>
            </div>

            {/* Microservicios: Order Service 1, Order Service 2 e INVENTORY SERVICE */}
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "10px" }}>
              {/* Order Service 1 */}
              <div
                style={{
                  background: activeInst === "ord1" ? "rgba(16, 185, 129, 0.35)" : "#0f172a",
                  border: `2px solid ${activeInst === "ord1" ? "#10b981" : "#334155"}`,
                  borderRadius: "10px",
                  padding: "8px",
                  boxShadow: activeInst === "ord1" ? "0 0 25px rgba(16, 185, 129, 0.6)" : "none",
                  transition: "all 0.15s ease-in-out",
                  transform: activeInst === "ord1" ? "scale(1.04)" : "scale(1)",
                }}
              >
                <div style={{ fontWeight: 800, color: activeInst === "ord1" ? "#34d399" : "#ffffff", fontSize: "12px" }}>
                  📦 order-service-1
                </div>
                <div style={{ fontSize: "10px", color: "#94a3b8" }}>:8081</div>
                <div style={{ fontSize: "9px", color: activeInst === "ord1" ? "#34d399" : "#38bdf8", fontWeight: 700 }}>
                  {activeInst === "ord1" ? "● ATENDIENDO" : "X-Instance-Id: 1"}
                </div>
              </div>

              {/* Order Service 2 */}
              <div
                style={{
                  background: activeInst === "ord2" ? "rgba(168, 85, 247, 0.35)" : "#0f172a",
                  border: `2px solid ${activeInst === "ord2" ? "#a855f7" : "#334155"}`,
                  borderRadius: "10px",
                  padding: "8px",
                  boxShadow: activeInst === "ord2" ? "0 0 25px rgba(168, 85, 247, 0.6)" : "none",
                  transition: "all 0.15s ease-in-out",
                  transform: activeInst === "ord2" ? "scale(1.04)" : "scale(1)",
                }}
              >
                <div style={{ fontWeight: 800, color: activeInst === "ord2" ? "#c084fc" : "#ffffff", fontSize: "12px" }}>
                  📦 order-service-2
                </div>
                <div style={{ fontSize: "10px", color: "#94a3b8" }}>:8083</div>
                <div style={{ fontSize: "9px", color: activeInst === "ord2" ? "#c084fc" : "#38bdf8", fontWeight: 700 }}>
                  {activeInst === "ord2" ? "● ATENDIENDO" : "X-Instance-Id: 2"}
                </div>
              </div>

              {/* INVENTORY SERVICE (AHORA INCLUIDO) */}
              <div
                style={{
                  background: activeInv ? "rgba(245, 158, 11, 0.35)" : "#0f172a",
                  border: `2px solid ${activeInv ? "#f59e0b" : "#334155"}`,
                  borderRadius: "10px",
                  padding: "8px",
                  boxShadow: activeInv ? "0 0 25px rgba(245, 158, 11, 0.6)" : "none",
                  transition: "all 0.15s ease-in-out",
                  transform: activeInv ? "scale(1.04)" : "scale(1)",
                }}
              >
                <div style={{ fontWeight: 800, color: activeInv ? "#fbbf24" : "#ffffff", fontSize: "12px" }}>
                  🏷️ inventory-svc
                </div>
                <div style={{ fontSize: "10px", color: "#94a3b8" }}>:8082</div>
                <div style={{ fontSize: "9px", color: activeInv ? "#fbbf24" : "#cbd5e1", fontWeight: 700 }}>
                  {activeInv ? "● RESERVANDO" : "Stock Mgmt"}
                </div>
              </div>
            </div>

            {/* Base de Datos: Clúster WAL Replicado con Iluminación Sincronizada */}
            <div style={{ background: "rgba(15, 23, 42, 0.95)", border: "1px dashed rgba(255, 255, 255, 0.2)", borderRadius: "12px", padding: "10px" }}>
              <div style={{ fontSize: "10px", color: "#94a3b8", textTransform: "uppercase", fontWeight: 700, marginBottom: "6px", display: "flex", justifyContent: "space-between" }}>
                <span>POSTGRESQL REPLICATED CLUSTER</span>
                <span style={{ color: activeDb === "primary" ? "#ef4444" : "#38bdf8" }}>
                  {activeDb === "primary" ? "● ESCRITURA EN PRIMARY" : activeDb !== "none" ? "● LECTURA BALANCEADA" : "IDLE"}
                </span>
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "8px", fontSize: "10px" }}>
                
                {/* PRIMARY (ILUMINADO EN ROJO / NARANJA AL HACER POST) */}
                <div
                  style={{
                    background: activeDb === "primary" ? "rgba(239, 68, 68, 0.4)" : "rgba(239, 68, 68, 0.1)",
                    border: `2px solid ${activeDb === "primary" ? "#ef4444" : "#7f1d1d"}`,
                    borderRadius: "8px",
                    padding: "8px 4px",
                    textAlign: "center",
                    boxShadow: activeDb === "primary" ? "0 0 25px rgba(239, 68, 68, 0.7)" : "none",
                    transform: activeDb === "primary" ? "scale(1.05)" : "scale(1)",
                    transition: "all 0.15s ease-in-out",
                  }}
                >
                  <strong style={{ color: "#f87171", fontSize: "11px" }}>Postgres Primary</strong>
                  <div style={{ color: activeDb === "primary" ? "#ffffff" : "#94a3b8", fontWeight: 700 }}>:5435</div>
                  <div style={{ fontSize: "9px", color: activeDb === "primary" ? "#fecaca" : "#ef4444", fontWeight: 800, marginTop: "2px" }}>
                    {activeDb === "primary" ? "★ WRITE COMMIT" : "Writes / Master"}
                  </div>
                </div>

                {/* RÉPLICA 1 (ILUMINADA EN CYAN AL HACER GET DE REPLICA 1) */}
                <div
                  style={{
                    background: activeDb === "rep1" ? "rgba(56, 189, 248, 0.4)" : "rgba(56, 189, 248, 0.1)",
                    border: `2px solid ${activeDb === "rep1" ? "#38bdf8" : "#0369a1"}`,
                    borderRadius: "8px",
                    padding: "8px 4px",
                    textAlign: "center",
                    boxShadow: activeDb === "rep1" ? "0 0 25px rgba(56, 189, 248, 0.7)" : "none",
                    transform: activeDb === "rep1" ? "scale(1.05)" : "scale(1)",
                    transition: "all 0.15s ease-in-out",
                  }}
                >
                  <strong style={{ color: "#38bdf8", fontSize: "11px" }}>postgres-replica1</strong>
                  <div style={{ color: activeDb === "rep1" ? "#ffffff" : "#94a3b8", fontWeight: 700 }}>:5433</div>
                  <div style={{ fontSize: "9px", color: activeDb === "rep1" ? "#e0f2fe" : "#38bdf8", fontWeight: 800, marginTop: "2px" }}>
                    {activeDb === "rep1" ? "★ READ (REPLICA_1)" : "Reads (50%)"}
                  </div>
                </div>

                {/* RÉPLICA 2 (ILUMINADA EN VERDE/AZUL AL HACER GET DE REPLICA 2) */}
                <div
                  style={{
                    background: activeDb === "rep2" ? "rgba(16, 185, 129, 0.4)" : "rgba(16, 185, 129, 0.1)",
                    border: `2px solid ${activeDb === "rep2" ? "#10b981" : "#047857"}`,
                    borderRadius: "8px",
                    padding: "8px 4px",
                    textAlign: "center",
                    boxShadow: activeDb === "rep2" ? "0 0 25px rgba(16, 185, 129, 0.7)" : "none",
                    transform: activeDb === "rep2" ? "scale(1.05)" : "scale(1)",
                    transition: "all 0.15s ease-in-out",
                  }}
                >
                  <strong style={{ color: "#34d399", fontSize: "11px" }}>postgres-replica2</strong>
                  <div style={{ color: activeDb === "rep2" ? "#ffffff" : "#94a3b8", fontWeight: 700 }}>:5434</div>
                  <div style={{ fontSize: "9px", color: activeDb === "rep2" ? "#d1fae5" : "#34d399", fontWeight: 800, marginTop: "2px" }}>
                    {activeDb === "rep2" ? "★ READ (REPLICA_2)" : "Reads (50%)"}
                  </div>
                </div>

              </div>
            </div>
          </div>

          <div style={{ fontSize: "11px", color: "#94a3b8", borderTop: "1px solid rgba(255,255,255,0.08)", paddingTop: "6px" }}>
            💡 <strong>Sincronización en Tiempo Real:</strong> Fíjate cómo cada fila impresa en la terminal ilumina su instancia y base de datos correspondiente a la izquierda.
          </div>
        </div>

        {/* PANEL DERECHO: Terminal Multi-Script (GET / POST / BRUNO TESTS) */}
        <div
          style={{
            background: "#05080f",
            border: "1px solid #1e293b",
            borderRadius: "18px",
            padding: "18px",
            display: "flex",
            flexDirection: "column",
            fontFamily: "'JetBrains Mono', 'Fira Code', monospace",
            boxShadow: "0 20px 40px rgba(0,0,0,0.6)",
            position: "relative",
            overflow: "hidden",
          }}
        >
          {/* Barra Superior con Pestañas de Scripts */}
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", borderBottom: "1px solid #1e293b", paddingBottom: "10px", marginBottom: "10px" }}>
            <div style={{ display: "flex", gap: "6px" }}>
              <div style={{ width: "12px", height: "12px", borderRadius: "50%", background: "#ef4444" }} />
              <div style={{ width: "12px", height: "12px", borderRadius: "50%", background: "#f59e0b" }} />
              <div style={{ width: "12px", height: "12px", borderRadius: "50%", background: "#10b981" }} />
            </div>

            {/* Pestañas de Scripts Activos */}
            <div style={{ display: "flex", gap: "8px" }}>
              <span style={{ fontSize: "11px", padding: "4px 10px", borderRadius: "6px", background: isPhase1GET ? "#0284c7" : "#1e293b", color: "#ffffff", fontWeight: 700, border: isPhase1GET ? "1px solid #38bdf8" : "none" }}>
                1. GET (Balanceo Réplicas)
              </span>
              <span style={{ fontSize: "11px", padding: "4px 10px", borderRadius: "6px", background: isPhase2POST ? "#ea580c" : "#1e293b", color: "#ffffff", fontWeight: 700, border: isPhase2POST ? "1px solid #fb923c" : "none" }}>
                2. POST (Primary + Stock)
              </span>
              <span style={{ fontSize: "11px", padding: "4px 10px", borderRadius: "6px", background: isPhase3Bruno ? "#16a34a" : "#1e293b", color: "#ffffff", fontWeight: 700, border: isPhase3Bruno ? "1px solid #4ade80" : "none" }}>
                3. Bruno Test Suite (6/6)
              </span>
            </div>
          </div>

          {/* VISTA 1: GET LOAD BALANCER */}
          {isPhase1GET && (
            <div style={{ fontSize: "12px", lineHeight: 1.5, flex: 1, display: "flex", flexDirection: "column" }}>
              <div style={{ color: "#38bdf8", marginBottom: "6px", fontWeight: 600 }}>
                $ ./scripts/experiments/watch-loadbalance.sh http://localhost:8080/api/orders 100 GET
              </div>

              <div style={{ color: "#94a3b8", borderBottom: "1px solid #1e293b", paddingBottom: "4px", display: "grid", gridTemplateColumns: "35px 55px 65px 1.5fr 1.1fr", fontSize: "11px", fontWeight: 700 }}>
                <span>#</span><span>Tipo</span><span>Estado</span><span>Instancia Servidora</span><span>Nodo BD</span>
              </div>

              <div style={{ display: "flex", flexDirection: "column", gap: "3px", marginTop: "4px", flex: 1 }}>
                {requestsDataGET.slice(0, getIndex + 1).map((row, idx) => (
                  <div key={idx} style={{ display: "grid", gridTemplateColumns: "35px 55px 65px 1.5fr 1.1fr", fontSize: "12px", padding: "2px 0", background: idx === getIndex ? "rgba(56, 189, 248, 0.15)" : "transparent", borderRadius: "4px" }}>
                    <span style={{ color: "#64748b" }}>{row.req}</span>
                    <span style={{ color: "#38bdf8", fontWeight: 700 }}>{row.meth}</span>
                    <span style={{ color: "#34d399", fontWeight: 700 }}>{row.status}</span>
                    <span style={{ color: row.targetInst === "ord1" ? "#34d399" : "#c084fc", fontWeight: 600 }}>{row.inst}</span>
                    <span style={{ color: row.node === "REPLICA_1" ? "#38bdf8" : "#34d399", fontWeight: 700 }}>{row.node}</span>
                  </div>
                ))}
              </div>

              <div style={{ background: "rgba(15, 23, 42, 0.95)", border: "1px solid rgba(56, 189, 248, 0.35)", borderRadius: "8px", padding: "10px 14px", marginTop: "auto", fontSize: "11px" }}>
                <strong style={{ color: "#38bdf8" }}>📊 P02 RECONCILIACIÓN (100 SOLICITUDES ESTABLES):</strong>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "6px", marginTop: "4px" }}>
                  <div>• order-service-1 (:8081): <strong style={{ color: "#34d399" }}>50 reqs (50%)</strong></div>
                  <div>• order-service-2 (:8083): <strong style={{ color: "#c084fc" }}>50 reqs (50%)</strong></div>
                  <div>• postgres-replica1: <strong style={{ color: "#38bdf8" }}>50 lecturas (50%)</strong></div>
                  <div>• postgres-replica2: <strong style={{ color: "#34d399" }}>50 lecturas (50%)</strong></div>
                </div>
              </div>
            </div>
          )}

          {/* VISTA 2: POST CREACIÓN EN PRIMARY */}
          {isPhase2POST && (
            <div style={{ fontSize: "12px", lineHeight: 1.5, flex: 1, display: "flex", flexDirection: "column" }}>
              <div style={{ color: "#fb923c", marginBottom: "6px", fontWeight: 600 }}>
                $ ./scripts/experiments/watch-loadbalance.sh http://localhost:8080/api/orders 4 POST
              </div>

              <div style={{ color: "#94a3b8", borderBottom: "1px solid #1e293b", paddingBottom: "4px", display: "grid", gridTemplateColumns: "35px 55px 65px 1.4fr 1.1fr", fontSize: "11px", fontWeight: 700 }}>
                <span>#</span><span>Tipo</span><span>Estado</span><span>Instancia Servidora</span><span>Nodo BD</span>
              </div>

              <div style={{ display: "flex", flexDirection: "column", gap: "4px", marginTop: "6px", flex: 1 }}>
                {requestsDataPOST.slice(0, postIndex + 1).map((row, idx) => (
                  <div key={idx} style={{ display: "grid", gridTemplateColumns: "35px 55px 65px 1.4fr 1.1fr", fontSize: "12px", padding: "3px 0", background: idx === postIndex ? "rgba(239, 68, 68, 0.15)" : "transparent", borderRadius: "4px" }}>
                    <span style={{ color: "#64748b" }}>{row.req}</span>
                    <span style={{ color: "#fb923c", fontWeight: 700 }}>{row.meth}</span>
                    <span style={{ color: "#34d399", fontWeight: 700 }}>{row.status}</span>
                    <span style={{ color: row.targetInst === "ord1" ? "#34d399" : "#c084fc", fontWeight: 600 }}>{row.inst}</span>
                    <span style={{ color: "#ef4444", fontWeight: 800 }}>{row.node}</span>
                  </div>
                ))}
              </div>

              <div style={{ background: "rgba(15, 23, 42, 0.95)", border: "1px solid rgba(251, 146, 60, 0.4)", borderRadius: "8px", padding: "10px 14px", marginTop: "auto", fontSize: "11px" }}>
                <strong style={{ color: "#fb923c" }}>📝 ESCRITURAS ESTRICTAS EN PRIMARY + RPC INVENTORY:</strong>
                <div style={{ color: "#cbd5e1", marginTop: "4px" }}>
                  Cada <code>POST</code> descuenta stock en <code>inventory-service</code> y se guarda en <code>PRIMARY</code>. Las réplicas solo se replican vía WAL asíncrono.
                </div>
              </div>
            </div>
          )}

          {/* VISTA 3: BRUNO COLLECTION TEST RUNNER */}
          {isPhase3Bruno && (
            <div style={{ fontSize: "12px", lineHeight: 1.5, flex: 1, display: "flex", flexDirection: "column" }}>
              <div style={{ color: "#4ade80", marginBottom: "6px", fontWeight: 600 }}>
                $ bru run tests/bruno/collection --env local
              </div>

              <div style={{ color: "#94a3b8", borderBottom: "1px solid #1e293b", paddingBottom: "4px", display: "grid", gridTemplateColumns: "2fr 60px 1.2fr 60px 70px", fontSize: "11px", fontWeight: 700 }}>
                <span>Test File</span><span>Método</span><span>Endpoint</span><span>Tiempo</span><span>Resultado</span>
              </div>

              <div style={{ display: "flex", flexDirection: "column", gap: "3px", marginTop: "4px", flex: 1 }}>
                {brunoTests.slice(0, brunoIndex + 1).map((t, idx) => (
                  <div key={idx} style={{ display: "grid", gridTemplateColumns: "2fr 60px 1.2fr 60px 70px", fontSize: "11px", padding: "2px 0", background: idx === brunoIndex ? "rgba(74, 222, 128, 0.15)" : "transparent", borderRadius: "4px" }}>
                    <span style={{ color: "#e2e8f0" }}>{t.name}</span>
                    <span style={{ color: t.method === "POST" ? "#fb923c" : "#38bdf8", fontWeight: 600 }}>{t.method}</span>
                    <span style={{ color: "#94a3b8" }}>{t.url}</span>
                    <span style={{ color: "#64748b" }}>{t.time}</span>
                    <span style={{ color: "#34d399", fontWeight: 800 }}>✓ {t.status}</span>
                  </div>
                ))}
              </div>

              <div style={{ background: "rgba(15, 23, 42, 0.95)", border: "1px solid rgba(74, 222, 128, 0.4)", borderRadius: "8px", padding: "10px 14px", marginTop: "auto", fontSize: "11px" }}>
                <strong style={{ color: "#4ade80" }}>🎉 BRUNO CLI TEST RESULTS: 6/6 PASSED (0 FAILED)</strong>
                <div style={{ color: "#cbd5e1", marginTop: "4px" }}>
                  Verificación completa de Discovery, Creación, Stock, Lectura Crítica y Rechazo de Stock Insuficiente.
                </div>
              </div>
            </div>
          )}

        </div>
      </div>

      {/* Footer */}
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          width: "100%",
          maxWidth: "1740px",
          background: "rgba(15, 23, 42, 0.8)",
          border: "1px solid rgba(255,255,255,0.08)",
          borderRadius: "12px",
          padding: "8px 20px",
          fontSize: "13px",
        }}
      >
        <div style={{ color: "#94a3b8" }}>
          Pruebas automatizadas: <code>tests/bruno/collection</code> & <code>scripts/experiments/test-p02-loadbalance.sh</code>
        </div>
        <div style={{ color: "#34d399", fontWeight: 700 }}>
          ✓ Verificación por Cabeceras: <code>X-Instance-Id</code> & <code>X-Database-Node: PRIMARY / REPLICA_1 / REPLICA_2</code>
        </div>
      </div>
    </div>
  );
};
