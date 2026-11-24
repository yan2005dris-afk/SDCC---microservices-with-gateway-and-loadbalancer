package com.distribuidos.order.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.distribuidos.order.observability.MdcKeys;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class RoutedReadExecutorTest {

  private PlatformTransactionManager transactionManager;
  private RoundRobinReplicaSelector selector;
  private List<DataSourceType> nodesSeen;

  @BeforeEach
  void setUp() {
    transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    Map<DataSourceType, DataSource> replicas = new LinkedHashMap<>();
    replicas.put(DataSourceType.REPLICA_1, mock(DataSource.class));
    replicas.put(DataSourceType.REPLICA_2, mock(DataSource.class));
    selector = new RoundRobinReplicaSelector(replicas);
    nodesSeen = new ArrayList<>();
  }

  private RoutedReadExecutor executor(boolean fallbackToPrimary) {
    return new RoutedReadExecutor(
        selector,
        transactionManager,
        new ReadRoutingProperties(
            fallbackToPrimary,
            Duration.ofSeconds(1),
            Duration.ofSeconds(5),
            Duration.ofSeconds(3)));
  }

  /** Consulta que registra el nodo fijado y falla por conexión en los nodos indicados. */
  private Supplier<String> query(Set<DataSourceType> failingNodes) {
    return () -> {
      DataSourceType node = RoutingContextHolder.get();
      nodesSeen.add(node);
      assertThat(MDC.get(MdcKeys.DB_NODE)).isEqualTo(node.name());
      if (failingNodes.contains(node)) {
        throw new DataAccessResourceFailureException(
            "down", new SQLTransientConnectionException("Connection is not available"));
      }
      return "data-from-" + node;
    };
  }

  @Test
  void ordinaryReadUsesSelectedReplica() {
    RoutedResult<String> result = executor(true).execute(ReadPolicy.ORDINARY, query(Set.of()));

    assertThat(result.node()).isEqualTo(DataSourceType.REPLICA_1);
    assertThat(result.value()).isEqualTo("data-from-REPLICA_1");
    assertThat(result.attempts()).isEqualTo(1);
  }

  @Test
  void connectionFailureMarksReplicaDownAndRetriesOnNextReplica() {
    RoutedResult<String> result =
        executor(true).execute(ReadPolicy.ORDINARY, query(Set.of(DataSourceType.REPLICA_1)));

    assertThat(result.node()).isEqualTo(DataSourceType.REPLICA_2);
    assertThat(result.attempts()).isEqualTo(2);
    assertThat(nodesSeen).containsExactly(DataSourceType.REPLICA_1, DataSourceType.REPLICA_2);
    assertThat(selector.isHealthy(DataSourceType.REPLICA_1)).isFalse();
  }

  @Test
  void eachAttemptRunsInItsOwnReadOnlyTransaction() {
    executor(true).execute(ReadPolicy.ORDINARY, query(Set.of(DataSourceType.REPLICA_1)));

    ArgumentCaptor<TransactionDefinition> definitions =
        ArgumentCaptor.forClass(TransactionDefinition.class);
    verify(transactionManager, times(2)).getTransaction(definitions.capture());
    assertThat(definitions.getAllValues())
        .allSatisfy(
            definition -> {
              assertThat(definition.isReadOnly()).isTrue();
              assertThat(definition.getPropagationBehavior())
                  .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            });
  }

  @Test
  void nonConnectionErrorIsNotRetried() {
    Supplier<String> badQuery =
        () -> {
          nodesSeen.add(RoutingContextHolder.get());
          throw new InvalidDataAccessResourceUsageException("bad sql");
        };

    assertThatThrownBy(() -> executor(true).execute(ReadPolicy.ORDINARY, badQuery))
        .isInstanceOf(InvalidDataAccessResourceUsageException.class);
    assertThat(nodesSeen).containsExactly(DataSourceType.REPLICA_1);
    assertThat(selector.isHealthy(DataSourceType.REPLICA_1)).isTrue();
  }

  @Test
  void allReplicasDownFallsBackToPrimary() {
    RoutedResult<String> result =
        executor(true)
            .execute(
                ReadPolicy.ORDINARY,
                query(Set.of(DataSourceType.REPLICA_1, DataSourceType.REPLICA_2)));

    assertThat(result.node()).isEqualTo(DataSourceType.PRIMARY);
    assertThat(result.attempts()).isEqualTo(3);
  }

  @Test
  void allReplicasDownWithoutFallbackIsUnavailable() {
    assertThatThrownBy(
            () ->
                executor(false)
                    .execute(
                        ReadPolicy.ORDINARY,
                        query(Set.of(DataSourceType.REPLICA_1, DataSourceType.REPLICA_2))))
        .isInstanceOf(ReadNodesUnavailableException.class);
  }

  @Test
  void criticalReadGoesStraightToPrimary() {
    RoutedResult<String> result = executor(true).execute(ReadPolicy.CRITICAL, query(Set.of()));

    assertThat(result.node()).isEqualTo(DataSourceType.PRIMARY);
    assertThat(nodesSeen).containsExactly(DataSourceType.PRIMARY);
  }

  @Test
  void primaryConnectionFailureIsReportedAsUnavailable() {
    assertThatThrownBy(
            () ->
                executor(true).execute(ReadPolicy.CRITICAL, query(Set.of(DataSourceType.PRIMARY))))
        .isInstanceOf(ReadNodesUnavailableException.class);
  }

  @Test
  void routingContextAndMdcAreClearedAfterSuccessAndFailure() {
    executor(true).execute(ReadPolicy.ORDINARY, query(Set.of(DataSourceType.REPLICA_1)));

    assertThat(RoutingContextHolder.get()).isNull();
    assertThat(MDC.get(MdcKeys.DB_NODE)).isNull();
  }

  /**
   * Caso detectado en CI: la conexión se cae a mitad de la consulta, Hikari la cierra y el rollback
   * falla con "Connection is closed". El error del rollback no debe ocultar el fallo de conexión.
   */
  @Test
  void connectionFailureIsRetriedEvenWhenRollbackFailsOnClosedConnection() {
    doThrow(rollbackOnClosedConnection()).when(transactionManager).rollback(any());

    RoutedResult<String> result =
        executor(true).execute(ReadPolicy.ORDINARY, query(Set.of(DataSourceType.REPLICA_1)));

    assertThat(result.node()).isEqualTo(DataSourceType.REPLICA_2);
    assertThat(nodesSeen).containsExactly(DataSourceType.REPLICA_1, DataSourceType.REPLICA_2);
    assertThat(selector.isHealthy(DataSourceType.REPLICA_1)).isFalse();
  }

  @Test
  void nonConnectionErrorIsStillNotRetriedWhenRollbackFails() {
    doThrow(rollbackOnClosedConnection()).when(transactionManager).rollback(any());
    Supplier<String> badQuery =
        () -> {
          nodesSeen.add(RoutingContextHolder.get());
          throw new InvalidDataAccessResourceUsageException("bad sql");
        };

    assertThatThrownBy(() -> executor(true).execute(ReadPolicy.ORDINARY, badQuery))
        .isInstanceOf(InvalidDataAccessResourceUsageException.class)
        .satisfies(
            ex -> assertThat(ex.getSuppressed()).hasOnlyElementsOfType(JpaSystemException.class));
    assertThat(nodesSeen).containsExactly(DataSourceType.REPLICA_1);
  }

  private static JpaSystemException rollbackOnClosedConnection() {
    return new JpaSystemException(
        new IllegalStateException(
            "Unable to rollback against JDBC Connection",
            new SQLException("Connection is closed")));
  }
}
