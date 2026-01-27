package dev.kruhlmann.imgfloat.repository;

import dev.kruhlmann.imgfloat.model.db.imgfloat.Channel;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelRepository extends JpaRepository<Channel, String> {
    List<Channel> findTop50ByBroadcasterContainingIgnoreCaseOrderByBroadcasterAsc(String broadcasterFragment);
}
