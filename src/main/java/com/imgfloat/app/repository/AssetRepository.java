package com.imgfloat.app.repository;

import com.imgfloat.app.model.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, String> {
    List<Asset> findByBroadcaster(String broadcaster);
    List<Asset> findByBroadcasterAndHiddenFalse(String broadcaster);
}
