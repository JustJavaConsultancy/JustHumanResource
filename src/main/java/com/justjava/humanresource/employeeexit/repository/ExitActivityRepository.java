package com.justjava.humanresource.employeeexit.repository; import com.justjava.humanresource.employeeexit.entity.ExitActivity; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List;
public interface ExitActivityRepository extends JpaRepository<ExitActivity,Long>{List<ExitActivity> findByExitCaseIdOrderByCreatedAt(Long id);}
