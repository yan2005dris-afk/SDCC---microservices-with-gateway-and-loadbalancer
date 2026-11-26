import React from "react";
import { Audio, staticFile, useCurrentFrame, useVideoConfig } from "remotion";

export const VideoGinoFull: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig(); // 30 fps -> 2686 frames (89.54 seg)
  const sec = frame / fps;

  // 13 BLOQUES EXACTOS DE SUBTÍTULOS SINCRONIZADOS CON LAS PAUSAS REALES DE GINO:
  const subtitles = [
    { start: 0.0, end: 3.6, text: "..." },
    { start: 3.6, end: 9.4, text: "Gracias Yandris. Mi nombre es Gino Bermúdez...", highlight: "Gino Bermúdez" },
    { start: 9.4, end: 15.5, text: "El cliente entra únicamente por el puerto :8080 del API Gateway.", highlight: ":8080 del API Gateway" },
    { start: 15.5, end: 20.8, text: "El Gateway no solo enruta a inventario, sino que implementa Spring Cloud LoadBalancer...", highlight: "Spring Cloud LoadBalancer" },
    { start: 20.8, end: 29.3, text: "...para distribuir el tráfico de pedidos entre dos instancias activas.", highlight: "dos instancias activas" },
    { start: 29.3, end: 37.4, text: "Detalle importante de diseño distribuido: que Eureka marque las instancias en estado UP...", highlight: "Eureka marque UP" },
    { start: 37.4, end: 44.8, text: "...NO garantiza disponibilidad inmediata durante los primeros segundos de arranque.", highlight: "NO garantiza disponibilidad inmediata" },
    { start: 44.8, end: 56.1, text: "Si el Gateway no está sincronizado, puede responder con 503 hasta estabilizar el descubrimiento.", highlight: "responder con 503" },
    { start: 56.1, end: 62.5, text: "Para comprobar el balanceo, enviamos solicitudes de tipo GET a /api/orders.", highlight: "solicitudes GET a /api/orders" },
    { start: 62.5, end: 70.5, text: "Observen la terminal: la 1ra responde order-service-1 (:8081) y la 2da order-service-2 (:8083)...", highlight: "order-service-1 (:8081) y order-service-2 (:8083)" },
    { start: 70.5, end: 76.0, text: "...y así sucesivamente con Round-Robin, certificado por la cabecera X-Instance-Id.", highlight: "Round-Robin con X-Instance-Id" },
    { start: 76.0, end: 82.1, text: "Como son consultas de lectura, fíjense en la columna de nodo de BD: ¡NINGUNA toca al Primary!", highlight: "¡NINGUNA toca al Primary!" },
    { start: 82.1, end: 89.5, text: "Se reparten de forma balanceada entre Réplica 1 y Réplica 2. Ahora le doy el paso a Allison...", highlight: "Réplica 1 y Réplica 2 -> Paso a Allison" },
  ];

  const currentSub = subtitles.find((s) => sec >= s.start && sec < s.end) || subtitles[subtitles.length - 1];

  // ESTADOS DE ARQUITECTURA SEGÚN EL AUDIO:
  const isIntro = sec < 9.4;
  const isGatewayRouting = sec >= 9.4 && sec < 29.3;
  const isEurekaWarning503 = sec >= 29.3 && sec < 56.1;
  const isGetTesting = sec >= 56.1 && sec < 76.0;
  const isReadSplittingReplicas = sec >= 76.0 && sec < 82.1;
  const isPassToAllison = sec >= 82.1;

  // En la fase de GETs (56.1s - 76.0s), alternar peticiones cada 2.5 segundos
  const getCycle = isGetTesting ? Math.floor((sec - 56.1) / 2.4) : 0;
  const activeInstance = isGetTesting ? (getCycle % 2 === 0 ? "ord1" : "ord2") : "none";
  const activeDbNode = isGetTesting 
    ? (getCycle % 2 === 0 ? "rep1" : "rep2")
    : isReadSplittingReplicas
    ? (Math.floor(sec * 2) % 2 === 0 ? "rep1" : "rep2")
    : isPassToAllison
    ? "primary"
    : "none";

  // Filas de consola dinámicas durante las pruebas de Gino
  const terminalRows = [
    { n: "1", m: "GET", s: "200", inst: "order-service-1:8081", db: "REPLICA_1", c: "#34d399" },
    { n: "2", m: "GET", s: "200", inst: "order-service-2:8083", db: "REPLICA_2", c: "#c084fc" },
    { n: "3", m: "GET", s: "200", inst: "order-service-1:8081", db: "REPLICA_2", c: "#34d399" },
    { n: "4", m: "GET", s: "200", inst: "order-service-2:8083", db: "REPLICA_1", c: "#c084fc" },
    { n: "5", m: "GET", s: "200", inst: "order-service-1:8081", db: "REPLICA_1", c: "#34d399" },
    { n: "6", m: "GET", s: "200", inst: "order-service-2:8083", db: "REPLICA_2", c: "#c084fc" },
    { n: "7", m: "GET", s: "200", inst: "order-service-1:8081", db: "REPLICA_2", c: "#34d399" },
    { n: "8", m: "GET", s: "200", inst: "order-service-2:8083", db: "REPLICA_1", c: "#c084fc" },
  ];
  const rowsVisible = isGetTesting || isReadSplittingReplicas || isPassToAllison 
    ? Math.min(8, Math.max(1, Math.floor((sec - 56.1) / 2.2))) 
    : 0;

  return (
    <div
      style={{
        flex: 1,
        backgroundColor: "#060911",
        backgroundImage: "radial-gradient(ellipse at 50% 10%, #0c4a6e 0%, #060911 80%)",
        color: "#ffffff",
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        alignItems: "center",
        fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, sans-serif",
        padding: "30px 50px 24px",
        overflow: "hidden",
        position: "relative",
      }}
    >
      {/* PISTA DE AUDIO REAL DE GINO */}
      <Audio src={staticFile("gino_voice.aac")} />

      {/* 1. HEADER SUPERIOR COMPACTO */}
      <div style={{ width: "100%", maxWidth: "1720px", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
          <div
            style={{
              width: "42px",
              height: "42px",
              borderRadius: "50%",
              background: "linear-gradient(135deg, #0284c7, #38bdf8)",
              display: "flex",
              justifyContent: "center",
              alignItems: "center",
              fontSize: "20px",
              boxShadow: "0 0 20px rgba(56, 189, 248, 0.5)",
            }}
          >
            🎙️
          </div>
          <div>
            <div style={{ fontSize: "18px", fontWeight: 800, color: "#ffffff" }}>
              Gino Bermúdez · Explicación en Vivo con Audio Real
            </div>
            <div style={{ fontSize: "13px", color: "#38bdf8", fontWeight: 600 }}>
              Prueba P02 · Balanceo de Carga HTTP & Read-Splitting PostgreSQL
            </div>
          </div>
        </div>

        {/* Tiempo y Progreso */}
        <div style={{ display: "flex", alignItems: "center", gap: "16px" }}>
          <div style={{ textAlign: "right" }}>
            <div style={{ fontSize: "20px", fontWeight: 900, color: "#38bdf8", fontFamily: "monospace" }}>
              {Math.floor(sec / 60)}:{Math.floor(sec % 60).toString().padStart(2, "0")} / 01:29
            </div>
            <div style={{ fontSize: "11px", color: "#94a3b8" }}>
              {isIntro && "Presentación"}
              {isGatewayRouting && "Gateway :8080 & LoadBalancer"}
              {isEurekaWarning503 && "Diseño: Eureka UP vs 503"}
              {isGetTesting && "Prueba GET: Round-Robin"}
              {isReadSplittingReplicas && "Lecturas en Réplicas (Primary 0%)"}
              {isPassToAllison && "Pase a Allison (POST)"}
            </div>
          </div>
          <div style={{ width: "70px", height: "6px", background: "#1e293b", borderRadius: "999px", overflow: "hidden" }}>
            <div style={{ width: `${(sec / 89.5) * 100}%`, height: "100%", background: "#38bdf8" }} />
          </div>
        </div>
      </div>

      {/* 2. DUAL PANEL: ARQUITECTURA ARCHIFY + TERMINAL EN VIVO */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "1.1fr 1fr",
          gap: "24px",
          width: "100%",
          maxWidth: "1720px",
          height: "610px",
        }}
      >
        {/* PANEL IZQUIERDO: ARQUITECTURA ILUSTRADA */}
        <div
          style={{
            background: "rgba(15, 23, 42, 0.85)",
            border: "1px solid rgba(56, 189, 248, 0.3)",
            borderRadius: "18px",
            padding: "20px",
            display: "flex",
            flexDirection: "column",
            justifyContent: "space-between",
            boxShadow: "0 20px 40px rgba(0,0,0,0.5)",
            backdropFilter: "blur(12px)",
          }}
        >
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", borderBottom: "1px solid rgba(255,255,255,0.08)", paddingBottom: "8px" }}>
            <span style={{ fontWeight: 800, fontSize: "14px", color: "#38bdf8", letterSpacing: "0.05em" }}>
              ARQUITECTURA DE ENRUTAMIENTO (SDCC-NETWORK)
            </span>
            <span style={{ fontSize: "11px", background: "rgba(2, 132, 199, 0.2)", color: "#7dd3fc", padding: "3px 8px", borderRadius: "6px", fontWeight: 700 }}>
              {isEurekaWarning503 ? "Sincronizando..." : "Docker Swarm/Compose"}
            </span>
          </div>

          <div style={{ display: "flex", flexDirection: "column", gap: "12px", margin: "auto 0" }}>
            {/* Top: Cliente HTTP */}
            <div style={{ display: "flex", justifyContent: "center", alignItems: "center", gap: "10px" }}>
              <div style={{ background: "#1e293b", border: "1px solid #475569", borderRadius: "10px", padding: "8px 16px", fontWeight: 700, fontSize: "13px" }}>
                🌐 Cliente Único (curl / Bruno / Web)
              </div>
              <span style={{ color: "#38bdf8", fontWeight: 900, fontSize: "14px" }}>➔ :8080</span>
            </div>

            {/* Gateway + Eureka */}
            <div style={{ display: "grid", gridTemplateColumns: "1.3fr 1fr", gap: "12px" }}>
              <div
                style={{
                  background: isEurekaWarning503 ? "rgba(239, 68, 68, 0.2)" : "rgba(2, 132, 199, 0.2)",
                  border: `2px solid ${isEurekaWarning503 ? "#ef4444" : "#0284c7"}`,
                  borderRadius: "12px",
                  padding: "10px",
                  textAlign: "center",
                  boxShadow: isEurekaWarning503 ? "0 0 25px rgba(239, 68, 68, 0.5)" : "0 0 20px rgba(2, 132, 199, 0.3)",
                  transition: "all 0.2s",
                }}
              >
                <div style={{ fontWeight: 800, color: isEurekaWarning503 ? "#fca5a5" : "#38bdf8", fontSize: "14px" }}>
                  ⚡ Spring Cloud Gateway :8080
                </div>
                <div style={{ fontSize: "11px", color: "#cbd5e1" }}>
                  {isEurekaWarning503 ? "Ventana 503 (Caché local)" : "Spring Cloud LoadBalancer"}
                </div>
              </div>

              <div
                style={{
                  background: "rgba(16, 185, 129, 0.15)",
                  border: "2px solid #10b981",
                  borderRadius: "12px",
                  padding: "10px",
                  textAlign: "center",
                }}
              >
                <div style={{ fontWeight: 800, color: "#34d399", fontSize: "14px" }}>🧭 Eureka Server</div>
                <div style={{ fontSize: "11px", color: "#34d399", fontWeight: 700 }}>:8761 · Estado UP</div>
              </div>
            </div>

            {/* Microservicios: Order 1, Order 2 e Inventory */}
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "10px" }}>
              {/* Order Service 1 */}
              <div
                style={{
                  background: activeInstance === "ord1" ? "rgba(16, 185, 129, 0.35)" : "#0f172a",
                  border: `2px solid ${activeInstance === "ord1" ? "#10b981" : "#334155"}`,
                  borderRadius: "10px",
                  padding: "8px",
                  boxShadow: activeInstance === "ord1" ? "0 0 25px rgba(16, 185, 129, 0.6)" : "none",
                  transform: activeInstance === "ord1" ? "scale(1.04)" : "scale(1)",
                  transition: "all 0.15s",
                }}
              >
                <div style={{ fontWeight: 800, color: activeInstance === "ord1" ? "#34d399" : "#ffffff", fontSize: "12px" }}>
                  📦 order-1 (:8081)
                </div>
                <div style={{ fontSize: "10px", color: activeInstance === "ord1" ? "#34d399" : "#94a3b8", fontWeight: 700 }}>
                  {activeInstance === "ord1" ? "● ATENDIENDO (GET)" : "X-Instance-Id: 1"}
                </div>
              </div>

              {/* Order Service 2 */}
              <div
                style={{
                  background: activeInstance === "ord2" ? "rgba(168, 85, 247, 0.35)" : "#0f172a",
                  border: `2px solid ${activeInstance === "ord2" ? "#a855f7" : "#334155"}`,
                  borderRadius: "10px",
                  padding: "8px",
                  boxShadow: activeInstance === "ord2" ? "0 0 25px rgba(168, 85, 247, 0.6)" : "none",
                  transform: activeInstance === "ord2" ? "scale(1.04)" : "scale(1)",
                  transition: "all 0.15s",
                }}
              >
                <div style={{ fontWeight: 800, color: activeInstance === "ord2" ? "#c084fc" : "#ffffff", fontSize: "12px" }}>
                  📦 order-2 (:8083)
                </div>
                <div style={{ fontSize: "10px", color: activeInstance === "ord2" ? "#c084fc" : "#94a3b8", fontWeight: 700 }}>
                  {activeInstance === "ord2" ? "● ATENDIENDO (GET)" : "X-Instance-Id: 2"}
                </div>
              </div>

              {/* Inventory Service */}
              <div
                style={{
                  background: isPassToAllison ? "rgba(245, 158, 11, 0.35)" : "#0f172a",
                  border: `2px solid ${isPassToAllison ? "#f59e0b" : "#334155"}`,
                  borderRadius: "10px",
                  padding: "8px",
                  boxShadow: isPassToAllison ? "0 0 25px rgba(245, 158, 11, 0.6)" : "none",
                  transform: isPassToAllison ? "scale(1.04)" : "scale(1)",
                  transition: "all 0.15s",
                }}
              >
                <div style={{ fontWeight: 800, color: isPassToAllison ? "#fbbf24" : "#ffffff", fontSize: "12px" }}>
                  🏷️ inventory (:8082)
                </div>
                <div style={{ fontSize: "10px", color: isPassToAllison ? "#fbbf24" : "#94a3b8", fontWeight: 700 }}>
                  {isPassToAllison ? "● RESERVA (POST)" : "Stock Mgmt"}
                </div>
              </div>
            </div>

            {/* Clúster PostgreSQL: Primary + Réplicas */}
            <div style={{ background: "rgba(15, 23, 42, 0.95)", border: "1px dashed rgba(255, 255, 255, 0.2)", borderRadius: "12px", padding: "10px" }}>
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: "10px", color: "#94a3b8", fontWeight: 800, marginBottom: "6px" }}>
                <span>POSTGRESQL CLUSTER (READ-SPLITTING)</span>
                <span style={{ color: isReadSplittingReplicas ? "#34d399" : "#38bdf8" }}>
                  {isReadSplittingReplicas ? "RÉPLICAS RECIBEN 100% LECTURAS" : "WAL REPLICATION"}
                </span>
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "8px" }}>
                
                {/* Primary */}
                <div
                  style={{
                    background: activeDbNode === "primary" ? "rgba(239, 68, 68, 0.4)" : "rgba(239, 68, 68, 0.1)",
                    border: `2px solid ${activeDbNode === "primary" ? "#ef4444" : "#7f1d1d"}`,
                    borderRadius: "8px",
                    padding: "6px 4px",
                    textAlign: "center",
                    transition: "all 0.15s",
                  }}
                >
                  <strong style={{ color: "#f87171", fontSize: "11px" }}>Primary (:5435)</strong>
                  <div style={{ fontSize: "9px", color: isReadSplittingReplicas ? "#64748b" : "#fca5a5", fontWeight: 700 }}>
                    {isReadSplittingReplicas ? "0% LECTURAS" : "POST Escrituras"}
                  </div>
                </div>

                {/* Réplica 1 */}
                <div
                  style={{
                    background: activeDbNode === "rep1" ? "rgba(56, 189, 248, 0.4)" : "rgba(56, 189, 248, 0.1)",
                    border: `2px solid ${activeDbNode === "rep1" ? "#38bdf8" : "#0369a1"}`,
                    borderRadius: "8px",
                    padding: "6px 4px",
                    textAlign: "center",
                    boxShadow: activeDbNode === "rep1" ? "0 0 20px rgba(56, 189, 248, 0.6)" : "none",
                    transform: activeDbNode === "rep1" ? "scale(1.05)" : "scale(1)",
                    transition: "all 0.15s",
                  }}
                >
                  <strong style={{ color: "#38bdf8", fontSize: "11px" }}>Réplica 1 (:5433)</strong>
                  <div style={{ fontSize: "9px", color: "#38bdf8", fontWeight: 800 }}>
                    50% LECTURAS GET
                  </div>
                </div>

                {/* Réplica 2 */}
                <div
                  style={{
                    background: activeDbNode === "rep2" ? "rgba(16, 185, 129, 0.4)" : "rgba(16, 185, 129, 0.1)",
                    border: `2px solid ${activeDbNode === "rep2" ? "#10b981" : "#047857"}`,
                    borderRadius: "8px",
                    padding: "6px 4px",
                    textAlign: "center",
                    boxShadow: activeDbNode === "rep2" ? "0 0 20px rgba(16, 185, 129, 0.6)" : "none",
                    transform: activeDbNode === "rep2" ? "scale(1.05)" : "scale(1)",
                    transition: "all 0.15s",
                  }}
                >
                  <strong style={{ color: "#34d399", fontSize: "11px" }}>Réplica 2 (:5434)</strong>
                  <div style={{ fontSize: "9px", color: "#34d399", fontWeight: 800 }}>
                    50% LECTURAS GET
                  </div>
                </div>

              </div>
            </div>

          </div>

          <div style={{ fontSize: "11px", color: "#94a3b8", borderTop: "1px solid rgba(255,255,255,0.08)", paddingTop: "6px" }}>
            💡 <strong>Diseño Académico:</strong> Desacople limpio entre HTTP Load Balancing y Read-Splitting físico de base de datos.
          </div>
        </div>

        {/* PANEL DERECHO: TERMINAL EN VIVO / GRÁFICAS DE PRUEBA */}
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
          }}
        >
          {/* Header Terminal */}
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", borderBottom: "1px solid #1e293b", paddingBottom: "8px", marginBottom: "10px" }}>
            <div style={{ display: "flex", gap: "6px" }}>
              <div style={{ width: "10px", height: "10px", borderRadius: "50%", background: "#ef4444" }} />
              <div style={{ width: "10px", height: "10px", borderRadius: "50%", background: "#f59e0b" }} />
              <div style={{ width: "10px", height: "10px", borderRadius: "50%", background: "#10b981" }} />
            </div>
            <div style={{ fontSize: "11px", color: "#64748b" }}>
              bash ./scripts/experiments/test-p02-loadbalance.sh
            </div>
            <div style={{ fontSize: "10px", color: "#38bdf8", background: "rgba(56, 189, 248, 0.15)", padding: "2px 8px", borderRadius: "4px" }}>
              MONITOR EXPERIMENTAL
            </div>
          </div>

          {/* Estado de Alerta 503 durante la explicación de Gino */}
          {isEurekaWarning503 ? (
            <div style={{ flex: 1, display: "flex", flexDirection: "column", justifyContent: "center", alignItems: "center", textAlign: "center", padding: "10px" }}>
              <div style={{ fontSize: "40px", marginBottom: "8px" }}>⚠️</div>
              <div style={{ fontSize: "18px", fontWeight: 800, color: "#f87171" }}>
                HTTP 503 Service Unavailable
              </div>
              <div style={{ fontSize: "13px", color: "#94a3b8", maxWidth: "420px", marginTop: "6px", lineHeight: 1.4 }}>
                "Eureka UP no garantiza disponibilidad inmediata. El Gateway espera sincronizar la lista de instancias antes de habilitar el enrutamiento."
              </div>
              <div style={{ marginTop: "18px", background: "rgba(239, 68, 68, 0.15)", border: "1px solid #ef4444", borderRadius: "8px", padding: "8px 16px", fontSize: "11px", color: "#fca5a5" }}>
                Registrado en evidencia: <code>docs/evidencias/P02-gateway-503.log</code>
              </div>
            </div>
          ) : (
            /* Salida de la Terminal Durante Régimen Estable */
            <div style={{ fontSize: "11px", lineHeight: 1.5, flex: 1, display: "flex", flexDirection: "column" }}>
              <div style={{ color: "#38bdf8", marginBottom: "6px", fontWeight: 600 }}>
                $ ./scripts/experiments/watch-loadbalance.sh http://localhost:8080/api/orders 100 GET
              </div>

              <div style={{ color: "#94a3b8", borderBottom: "1px solid #1e293b", paddingBottom: "3px", display: "grid", gridTemplateColumns: "30px 45px 55px 1.5fr 1.1fr", fontSize: "10px", fontWeight: 700 }}>
                <span>#</span><span>Tipo</span><span>Estado</span><span>X-Instance-Id</span><span>Nodo BD</span>
              </div>

              {/* Filas */}
              <div style={{ display: "flex", flexDirection: "column", gap: "2px", marginTop: "4px", flex: 1 }}>
                {terminalRows.slice(0, rowsVisible).map((row, idx) => (
                  <div key={idx} style={{ display: "grid", gridTemplateColumns: "30px 45px 55px 1.5fr 1.1fr", fontSize: "11px", padding: "2px 0", background: idx % 2 === 0 ? "rgba(255,255,255,0.02)" : "transparent" }}>
                    <span style={{ color: "#64748b" }}>{row.n}</span>
                    <span style={{ color: "#38bdf8", fontWeight: 700 }}>{row.m}</span>
                    <span style={{ color: "#34d399", fontWeight: 800 }}>{row.s}</span>
                    <span style={{ color: row.c, fontWeight: 700 }}>{row.inst}</span>
                    <span style={{ color: row.db === "REPLICA_1" ? "#38bdf8" : "#34d399", fontWeight: 700 }}>{row.db}</span>
                  </div>
                ))}
              </div>

              {/* Resumen Final de Gino */}
              <div
                style={{
                  background: "rgba(15, 23, 42, 0.95)",
                  border: "1px solid rgba(56, 189, 248, 0.35)",
                  borderRadius: "10px",
                  padding: "10px 14px",
                  marginTop: "auto",
                  fontSize: "11px",
                }}
              >
                <div style={{ color: "#38bdf8", fontWeight: 800, marginBottom: "4px" }}>
                  📊 TOTALES MEDIDOS EN PRUEBA P02 (100 SOLICITUDES):
                </div>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "6px" }}>
                  <div>• order-service-1 (:8081): <strong style={{ color: "#34d399" }}>50 (50%)</strong></div>
                  <div>• order-service-2 (:8083): <strong style={{ color: "#c084fc" }}>50 (50%)</strong></div>
                  <div>• postgres-replica1: <strong style={{ color: "#38bdf8" }}>50 lecturas (50%)</strong></div>
                  <div>• postgres-replica2: <strong style={{ color: "#34d399" }}>50 lecturas (50%)</strong></div>
                  <div style={{ gridColumn: "span 2", color: "#f87171", fontWeight: 700, borderTop: "1px solid rgba(255,255,255,0.08)", paddingTop: "4px" }}>
                    • postgres-primary: 0 lecturas (100% Protegido)
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 3. SUBTÍTULO MODERNO DINÁMICO ESTILO YOUTUBE / TIKTOK (EN LA PARTE INFERIOR) */}
      <div
        style={{
          width: "100%",
          maxWidth: "1400px",
          background: "rgba(15, 23, 42, 0.9)",
          border: "1.5px solid rgba(56, 189, 248, 0.5)",
          borderRadius: "16px",
          padding: "14px 28px",
          textAlign: "center",
          boxShadow: "0 10px 30px rgba(0,0,0,0.7), 0 0 20px rgba(56, 189, 248, 0.2)",
          backdropFilter: "blur(14px)",
        }}
      >
        <div style={{ fontSize: "11px", color: "#38bdf8", textTransform: "uppercase", fontWeight: 800, letterSpacing: "0.1em", marginBottom: "4px" }}>
          VOZ EN VIVO · GINO BERMÚDEZ
        </div>
        <div style={{ fontSize: "22px", fontWeight: 700, color: "#ffffff", letterSpacing: "-0.01em" }}>
          {currentSub.text}
        </div>
      </div>
    </div>
  );
};
