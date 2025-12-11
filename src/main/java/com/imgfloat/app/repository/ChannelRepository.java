package com.imgfloat.app.repository;

import com.imgfloat.app.model.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChannelRepository extends JpaRepository<Channel, String> {
    List<Channel> findTop50ByBroadcasterContainingIgnoreCaseOrderByBroadcasterAsc(String broadcaster);

    List<Channel> findTop50ByOrderByBroadcasterAsc();

    List<Channel> findByAdminsContaining(String username);
}
