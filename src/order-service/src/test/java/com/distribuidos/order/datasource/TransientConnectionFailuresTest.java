package com.distribuidos.order.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import org.hibernate.exception.JDBCConnectionException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.transaction.CannotCreateTransactionException;

class TransientConnectionFailuresTest {

  @Test
  void hikariTimeoutIsTransient() {
    // Observado en el clúster: "HikariReplica1Pool - Connection is not available".
    assertThat(
            TransientConnectionFailures.isTransient(
                new SQLTransientConnectionException("Connection is not available")))
        .isTrue();
  }

  @Test
  void wrappedConnectionFailureIsTransient() {
    SQLException closed = new SQLException("This connection has been closed.", "08003");
    RuntimeException wrapped =
        new DataAccessResourceFailureException(
            "could not prepare statement", new JDBCConnectionException("jdbc", closed));

    assertThat(TransientConnectionFailures.isTransient(wrapped)).isTrue();
  }

  @Test
  void poolInitializationFailureWithConnectionSqlStateIsTransient() {
    RuntimeException poolInit =
        new RuntimeException(
            "Failed to initialize pool", new SQLException("Connection refused", "08001"));

    assertThat(TransientConnectionFailures.isTransient(poolInit)).isTrue();
  }

  @Test
  void cannotCreateTransactionIsTransient() {
    assertThat(
            TransientConnectionFailures.isTransient(
                new CannotCreateTransactionException("no connection")))
        .isTrue();
  }

  @Test
  void sqlErrorIsNotTransient() {
    RuntimeException grammar =
        new InvalidDataAccessResourceUsageException(
            "bad sql", new SQLException("relation does not exist", "42P01"));

    assertThat(TransientConnectionFailures.isTransient(grammar)).isFalse();
  }

  @Test
  void plainRuntimeErrorIsNotTransient() {
    assertThat(TransientConnectionFailures.isTransient(new IllegalStateException("bug"))).isFalse();
    assertThat(TransientConnectionFailures.isTransient(null)).isFalse();
  }
}
