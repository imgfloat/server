package dev.kruhlmann.imgfloat.repository;

import dev.kruhlmann.imgfloat.model.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChannelRepository extends JpaRepository<Channel, String> {
    List<Channel> findTop50ByBroadcasterContainingIgnoreCaseOrderByBroadcasterAsc(String broadcasterFragment);
}
