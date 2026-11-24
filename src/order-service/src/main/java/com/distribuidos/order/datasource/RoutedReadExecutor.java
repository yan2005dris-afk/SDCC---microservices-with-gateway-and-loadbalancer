package com.distribuidos.order.datasource;

import com.distribuidos.order.observability.MdcKeys;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ejecuta lecturas aplicando la política de enrutamiento y el failover entre réplicas.
 *
 * <ul>
 *   <li>{@link ReadPolicy#CRITICAL}: siempre en el primario (read-your-writes, P07).
 *   <li>{@link ReadPolicy#ORDINARY}: en la réplica que indique el {@link ReplicaSelector}. Ante un
 *       fallo de conexión, la réplica se retira del reparto y se reintenta en la siguiente (P06),
 *       como máximo una vez por réplica. Si no queda ninguna, se lee del primario cuando {@code
 *       fallback-to-primary} está activo.
 * </ul>
 *
 * <p>Cada intento corre en su <b>propia transacción</b> de solo lectura. Así, el fallo de un nodo
 * no marca como rollback-only la transacción del reintento, que es lo que provocaba el 500 de
 * ORD-01. La consulta recibida debe devolver datos ya materializados (DTO), porque fuera de la
 * transacción la sesión JPA está cerrada.
 */
@Component
public class RoutedReadExecutor {

  private static final Logger log = LoggerFactory.getLogger(RoutedReadExecutor.class);

  private final ReplicaSelector selector;
  private final TransactionTemplate readTransaction;
  private final boolean fallbackToPrimary;

  /**
   * Crea el executor.
   *
   * @param selector selección de réplicas.
   * @param transactionManager gestor de transacciones JPA.
   * @param properties configuración del enrutamiento de lecturas.
   */
  public RoutedReadExecutor(
      ReplicaSelector selector,
      PlatformTransactionManager transactionManager,
      ReadRoutingProperties properties) {
    this.selector = selector;
    this.fallbackToPrimary = properties.fallbackToPrimary();
    this.readTransaction = new TransactionTemplate(transactionManager);
    this.readTransaction.setReadOnly(true);
    this.readTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /**
   * Ejecuta una lectura según la política indicada.
   *
   * @param policy política de consistencia.
   * @param query consulta a ejecutar; debe devolver datos materializados.
   * @param <T> tipo del resultado.
   * @return resultado junto con el nodo que respondió.
   * @throws ReadNodesUnavailableException si ningún nodo pudo atender la lectura.
   */
  public <T> RoutedResult<T> execute(ReadPolicy policy, Supplier<T> query) {
    if (policy == ReadPolicy.CRITICAL) {
      return runOnPrimary(policy, query, 1);
    }
    RuntimeException lastFailure = null;
    int attempt = 0;
    while (attempt < selector.replicaCount()) {
      Optional<DbNode> candidate = selector.next();
      if (candidate.isEmpty()) {
        break;
      }
      attempt++;
      DbNode replica = candidate.get();
      try {
        return runOn(replica, policy, query, attempt);
      } catch (RuntimeException ex) {
        if (!TransientConnectionFailures.isTransient(ex)) {
          throw ex;
        }
        selector.markDown(replica);
        lastFailure = ex;
      }
    }
    if (!fallbackToPrimary) {
      throw new ReadNodesUnavailableException(lastFailure);
    }
    log.warn("db.route op=READ policy={} fallback=PRIMARY reason=no-healthy-replica", policy);
    return runOnPrimary(policy, query, attempt + 1);
  }

  private <T> RoutedResult<T> runOnPrimary(ReadPolicy policy, Supplier<T> query, int attempt) {
    try {
      return runOn(DbNode.PRIMARY, policy, query, attempt);
    } catch (RuntimeException ex) {
      if (TransientConnectionFailures.isTransient(ex)) {
        throw new ReadNodesUnavailableException(ex);
      }
      throw ex;
    }
  }

  private <T> RoutedResult<T> runOn(
      DbNode node, ReadPolicy policy, Supplier<T> query, int attempt) {
    long start = System.nanoTime();
    RoutingContextHolder.set(node);
    MDC.put(MdcKeys.DB_NODE, node.name());
    AtomicReference<RuntimeException> queryFailure = new AtomicReference<>();
    try {
      T value =
          readTransaction.execute(
              status -> {
                try {
                  return query.get();
                } catch (RuntimeException ex) {
                  queryFailure.set(ex);
                  throw ex;
                }
              });
      log.info(
          "db.route op=READ policy={} node={} attempt={} result=OK durationMs={}",
          policy,
          node,
          attempt,
          elapsedMillis(start));
      return new RoutedResult<>(value, node, attempt);
    } catch (RuntimeException ex) {
      RuntimeException failure = originalFailure(queryFailure.get(), ex);
      log.warn(
          "db.route op=READ policy={} node={} attempt={} result=FAILED error={} durationMs={}",
          policy,
          node,
          attempt,
          failure.getClass().getSimpleName(),
          elapsedMillis(start));
      throw failure;
    } finally {
      RoutingContextHolder.clear();
      MDC.remove(MdcKeys.DB_NODE);
    }
  }

  /**
   * Devuelve el error de la consulta aunque el rollback posterior haya fallado.
   *
   * <p>Si la conexión se cae a mitad de la consulta, Hikari la cierra y el rollback de {@code
   * TransactionTemplate} falla con "Connection is closed". Spring propaga entonces el error del
   * rollback y oculta el de la consulta, que es el que indica un fallo de conexión reintentable.
   * Por eso se decide con el error original y el del rollback queda como {@code suppressed}.
   */
  private static RuntimeException originalFailure(RuntimeException query, RuntimeException thrown) {
    if (query == null || query == thrown) {
      return thrown;
    }
    query.addSuppressed(thrown);
    return query;
  }

  private static long elapsedMillis(long startNanos) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
  }
}
