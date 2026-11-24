package com.ihit.expense_tracker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserProfileRepository extends JpaRepository<UserProfile, String> {

    @Query("select profile from UserProfile profile where lower(profile.email) in :emails")
    List<UserProfile> findByEmailIgnoreCaseIn(@Param("emails") List<String> emails);
}
