package com.daygenie.repository;

import com.daygenie.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    /** Only PENDING or IN_PROGRESS tasks, sorted by scheduled time */
    @Query("SELECT t FROM Task t WHERE t.user.id = :userId " +
            "AND t.status NOT IN ('COMPLETED', 'CANCELLED') " +
            "ORDER BY t.scheduledTime ASC")
    List<Task> findActiveTasksByUserId(@Param("userId") Long userId);

    /** Active tasks within a date range (for today's view) */
    @Query("SELECT t FROM Task t WHERE t.user.id = :userId " +
            "AND t.status NOT IN ('COMPLETED', 'CANCELLED') " +
            "AND t.scheduledTime BETWEEN :start AND :end " +
            "ORDER BY t.scheduledTime ASC")
    List<Task> findActiveTasksForDateRange(@Param("userId") Long userId,
                                           @Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end);

    /** All tasks (any status) for a user */
    List<Task> findByUserIdOrderByScheduledTimeAsc(Long userId);

    List<Task> findByUserIdAndStatusOrderByScheduledTimeAsc(Long userId, TaskStatus status);

    @Query("SELECT t FROM Task t WHERE t.status = 'PENDING' " +
            "AND t.scheduledTime BETWEEN :now AND :cutoff")
    List<Task> findUpcomingPendingTasks(@Param("now") LocalDateTime now,
                                        @Param("cutoff") LocalDateTime cutoff);

    List<Task> findByUserIdAndCategory(Long userId, TaskCategory category);
}