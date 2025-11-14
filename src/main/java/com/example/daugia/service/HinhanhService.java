package com.example.daugia.service;

import com.example.daugia.entity.Hinhanh;
import com.example.daugia.entity.Sanpham;
import com.example.daugia.repository.HinhanhRepository;
import com.example.daugia.repository.SanphamRepository;
import com.example.daugia.service.storage.SupabaseStorageService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
public class HinhanhService {
    private final HinhanhRepository hinhanhRepository;
    private final SanphamRepository sanphamRepository;
    private final SupabaseStorageService storage;

    @Value("${storage.max-size-mb:5}")
    private int maxSizeMb;

    public HinhanhService(HinhanhRepository hinhanhRepository,
                          SanphamRepository sanphamRepository,
                          SupabaseStorageService storage) {
        this.hinhanhRepository = hinhanhRepository;
        this.sanphamRepository = sanphamRepository;
        this.storage = storage;
    }

    public List<Hinhanh> findAll() {
        return hinhanhRepository.findAll();
    }

    @Transactional
    public List<Hinhanh> saveFiles(String masp, List<MultipartFile> files) throws Exception {
        Sanpham sanpham = sanphamRepository.findById(masp)
                .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không tồn tại"));
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Không có file nào được gửi lên");
        }

        List<Hinhanh> savedImages = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            SupabaseStorageService.UploadResult up = storage.uploadProductImage(masp, file, maxSizeMb);
            Hinhanh h = new Hinhanh();
            h.setTenanh(up.key());   // Lưu KEY: <bucket>/<path>
            h.setSanPham(sanpham);
            savedImages.add(hinhanhRepository.save(h));
        }
        return savedImages;
    }

    @Transactional
    public List<Hinhanh> updateFiles(String masp, List<MultipartFile> files) throws Exception {
        Sanpham sanpham = sanphamRepository.findById(masp)
                .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không tồn tại"));
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Không có file nào được gửi lên");
        }

        List<Hinhanh> currentImages = sanpham.getHinhAnh();
        if (currentImages == null) currentImages = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            if (file.isEmpty()) continue;

            SupabaseStorageService.UploadResult up = storage.uploadProductImage(masp, file, maxSizeMb);

            if (i < currentImages.size()) {
                // Xóa object cũ trên Supabase
                String oldKey = currentImages.get(i).getTenanh();
                try { storage.deleteObject(oldKey); } catch (Exception ignored) {}

                currentImages.get(i).setTenanh(up.key());
                hinhanhRepository.save(currentImages.get(i));
            } else {
                Hinhanh newImg = new Hinhanh();
                newImg.setTenanh(up.key());
                newImg.setSanPham(sanpham);
                hinhanhRepository.save(newImg);
                currentImages.add(newImg);
            }
        }

        // Giữ tối đa 3 ảnh
        if (currentImages.size() > 3) {
            for (int i = 3; i < currentImages.size(); i++) {
                try { storage.deleteObject(currentImages.get(i).getTenanh()); } catch (Exception ignored) {}
                hinhanhRepository.delete(currentImages.get(i));
            }
            currentImages = currentImages.subList(0, 3);
        }

        sanpham.setHinhAnh(currentImages);
        sanphamRepository.save(sanpham);
        return currentImages;
    }
}