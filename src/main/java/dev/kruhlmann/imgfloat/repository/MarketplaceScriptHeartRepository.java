package dev.kruhlmann.imgfloat.repository;

import dev.kruhlmann.imgfloat.model.MarketplaceScriptHeart;
import dev.kruhlmann.imgfloat.model.MarketplaceScriptHeartId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketplaceScriptHeartRepository
    extends JpaRepository<MarketplaceScriptHeart, MarketplaceScriptHeartId> {
    interface ScriptHeartCount {
        String getScriptId();
        long getHeartCount();
    }

    @Query(
        """
        SELECT h.scriptId AS scriptId, COUNT(h) AS heartCount
        FROM MarketplaceScriptHeart h
        WHERE h.scriptId IN :scriptIds
        GROUP BY h.scriptId
        """
    )
    List<ScriptHeartCount> countByScriptIds(@Param("scriptIds") Collection<String> scriptIds);

    List<MarketplaceScriptHeart> findByUsernameAndScriptIdIn(String username, Collection<String> scriptIds);

    boolean existsByScriptIdAndUsername(String scriptId, String username);

    long countByScriptId(String scriptId);

    void deleteByScriptIdAndUsername(String scriptId, String username);

    void deleteByUsername(String username);
}
