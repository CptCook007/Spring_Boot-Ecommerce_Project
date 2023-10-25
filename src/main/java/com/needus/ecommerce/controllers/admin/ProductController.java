package com.needus.ecommerce.controllers.admin;

import com.needus.ecommerce.entity.product.*;
import com.needus.ecommerce.entity.user.UserInformation;
import com.needus.ecommerce.exceptions.ResourceNotFoundException;
import com.needus.ecommerce.exceptions.TechnicalIssueException;
import com.needus.ecommerce.model.product.ProductDto;
import com.needus.ecommerce.repository.product.ProductsRepository;
import com.needus.ecommerce.service.product.*;
import com.needus.ecommerce.service.user.UserInformationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Controller
@Slf4j
@RequestMapping("/admin/products")
public class ProductController {
    //field injections
    @Autowired
    private UserInformationService userInformationService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private BrandService brandService;
    @Autowired
    private ProductFilterService filterService;
    @Autowired
    private ProductService productService;
    @Autowired
    private ProductImageService productImageService;
    @Autowired
    private ProductsRepository productsRepository;

    @GetMapping("/list")
    public String products(Model model, HttpServletRequest request,
                           @RequestParam(defaultValue = "1") int pageNo,
                           @RequestParam(defaultValue = "10") int pageSize) throws TechnicalIssueException {
        Page<Products> products;
        try {
            products = productService.findAllProducts(pageNo,pageSize);
        } catch (Exception e) {
            log.error("An error occurred while fetching the products",e);
            throw new TechnicalIssueException("An error occurred while fetching products", e);
        }
        Page<ProductDto> productDto = products.map(ProductDto::new);
        model.addAttribute("requestURI", request.getRequestURI());
        model.addAttribute("products", productDto);
        return "admin/products";
    }
    @GetMapping("/addProduct")
    public String addProducts(Model model, HttpServletRequest request) {
        List<Categories> categories;
        try{
            categories = categoryService.findAllNonDeletedCategories();
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new TechnicalIssueException("An error occurred while fetching the categories from the CategoryService", e);
        }
        List<Brands> brands;
        try {
            brands = brandService.findAllNonDeletedBrands();
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new TechnicalIssueException("An error occurred while fetching the brands from the BrandService", e);
        }
        List<ProductFilters> filters;
        try {
            filters = filterService.findAllFilters();
        } catch (Exception e) {
            e.printStackTrace();
            throw new TechnicalIssueException("An error occurred while fetching the filter tags from the ProductFilterService", e);
        }
        model.addAttribute("requestURI", request.getRequestURI());
        model.addAttribute("category", categories);
        model.addAttribute("brand", brands);
        model.addAttribute("filter", filters);
        return "admin/add_product";
    }
    @PostMapping("/addProduct/save")
    public String saveProduct(
        @RequestParam(name="brandId") Long brandId,
        @RequestParam(name="categoryId") Long categoryId,
        @RequestParam(name="productName") String productName,
        @RequestParam(name="description") String description,
        @RequestParam(name="productImages") List<MultipartFile> imageFiles,
        @RequestParam(name="productPrice") Float price,
        @RequestParam(name="productStock") Integer stock,
        @RequestParam(name="productFilters") List<ProductFilters> filters,
        Model model, RedirectAttributes ra
    ) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = auth.getName();
        Brands brand;
        Categories category;
        UserInformation currentUserInfo;
        try {
            currentUserInfo = userInformationService.findUserByName(currentUser);
            brand = brandService.findBrandById(brandId);
            category = categoryService.findCatgeoryById(categoryId);
        } catch (Exception e) {
            e.printStackTrace();
            throw new TechnicalIssueException("Something went wrong while fetching information", e);
        }
        Products product = new Products();
        product.setProductName(productName);
        Products tempProduct;
        try {
            tempProduct = productService.save(product);
        }
        catch (Exception e){
            log.error("Something went wrong while temporarily saving the product");
            throw new TechnicalIssueException("Something went wrong while temporarily saving the product");
        }
        List<ProductImages> images = new ArrayList<>();
        try {
            for (MultipartFile imageFile : imageFiles) {
                String fileName = fileUploadDir(imageFile);
                ProductImages imageObj = new ProductImages(fileName, tempProduct);
                imageObj = productImageService.save(imageObj);
                images.add(imageObj);
            }
        }
        catch (Exception e){
            log.error("Something went wrong while saving the image");
            throw new TechnicalIssueException("Something went wrong while saving the image");
        }
        product.setImages(images);
        product.setDescription(description);
        product.setBrands(brand);
        product.setCategories(category);
        product.setProductPrice(price);
        product.setUserInformation(currentUserInfo);
        product.setStock(stock);
        product.setProductFilters(filters);
        try {
            productService.save(product);
        }
        catch (Exception e){
            e.printStackTrace();
            log.error("Something went wrong while saving the product",e);
            throw new TechnicalIssueException("Something went wrong while saving the product",e);
        }
        ra.addFlashAttribute("message", "Product added successfully");
        return "redirect:/admin/products/list";
    }

    @PostMapping("/block/{id}")
    public String productBlock(RedirectAttributes redirectAttributes,
                               @PathVariable(name="id") Long productId){
        Products products;
        try {
            productService.blockProduct(productId);
            products = productService.findProductById(productId);
        }
        catch (Exception e){
            log.error("Something went wrong while blocking the product");
            throw new TechnicalIssueException("Something went wrong while blocking the product");
        }
        if(!products.isProductStatus()) {
            redirectAttributes.addFlashAttribute("message", "Product is Blocked");
        }
        else {
            redirectAttributes.addFlashAttribute("message","Product is Active");
        }
        return "redirect:/admin/products/list";
    }
    @GetMapping("/editProduct/{id}")
    public String productEdit(HttpServletRequest request,@PathVariable(name = "id") Long id,Model model){
        if(!productService.existsById(id)){
            throw new ResourceNotFoundException("Product not found");
        }
        Products product = productService.findProductById(id);
        Brands productBrand = product.getBrands();
        Categories productCategories = product.getCategories();
        List<Brands> brands = brandService.findAllNonDeletedBrands();
        List<Categories> categories = categoryService.findAllNonDeletedCategories();
        List<ProductFilters> filters = filterService.findAllFilters();
        model.addAttribute("productCategory",productCategories);
        model.addAttribute("requestURI",request.getRequestURI());
        model.addAttribute("category",categories);
        model.addAttribute("brand",brands);
        model.addAttribute("filter",filters);
        model.addAttribute("product", product);
        model.addAttribute("productBrand",productBrand);
        return "admin/editProducts";
    }
    @PostMapping("/editProduct/edit/{id}")
    public String productUpdate(
        @PathVariable(name="id") Long productId,
        @RequestParam(name="brandId",required = false) Long brandId,
        @RequestParam(name="categoryId",required = false) Long categoryId,
        @RequestParam(name="productName",required = false) String productName,
        @RequestParam(name="description",required = false) String description,
        @RequestParam(name="productImages",required = false) List<MultipartFile> imageFiles,
        @RequestParam(name="productPrice",required = false) Float price,
        @RequestParam(name="productStock",required = false) Integer stock,
        @RequestParam(name="productFilters",required = false) List<ProductFilters> filters,
        Model model,RedirectAttributes ra
    ) throws IOException {
        if(!productService.existsById(productId)){
            throw new ResourceNotFoundException("Product not found");
        }
        Products productDetails;
        try {
            productDetails = productService.findProductById(productId);
        }
        catch (Exception e){
            log.error("Something went wrong while fetching the product");
            throw new TechnicalIssueException("Something went wrong while fetching the product");
        }
        Brands brand = brandService.findBrandById(brandId);
        Categories category = categoryService.findCatgeoryById(categoryId);
        if(!productDetails.getProductName().equals(productName)||!productName.isEmpty()) {
            log.warn("Product name has been changed");
            productDetails.setProductName(productName);
        }
        Products tempProduct;
        try {
            tempProduct = productService.save(productDetails);
        }
        catch (Exception e){
            log.error("Something went wrong while temporarily saving the product");
            throw  new TechnicalIssueException("Something went wrong while temporarily saving the product");
        }
        List<ProductImages> images = new ArrayList<>();
        try {
            if (!imageFiles.isEmpty()) {
                for (MultipartFile imageFile : imageFiles) {
                    if (!imageFile.isEmpty()) {
                        productImageService.removeProductImages(productDetails.getImages());
                        String fileName = fileUploadDir(imageFile);
                        ProductImages imageObj = new ProductImages(fileName, tempProduct);
                        imageObj = productImageService.save(imageObj);
                        images.add(imageObj);
                    }
                }
                productDetails.getImages().clear();
            }
        }
        catch (Exception e){
            log.error("something went wrong while saving the image of the product");
            throw new TechnicalIssueException("something went wrong while saving the image of the product");
        }
        if(!images.isEmpty()) {
            log.warn("Images has been changed");
            productDetails.setImages(images);
        }
        if(Objects.nonNull(description)&&!description.isEmpty()) {
            log.warn("Description has been changed");
            productDetails.setDescription(description);
        }
        if(!productDetails.getBrands().equals(brand)) {
            log.warn("Brand has been changed");
            productDetails.setBrands(brand);
        }
        if(!productDetails.getCategories().equals(category)) {
            log.warn("Category has been changed");
            productDetails.setCategories(category);
        }
        if(Objects.nonNull(stock)&&productDetails.getStock().equals(stock)) {
            log.warn("Stock has been changed");
            productDetails.setStock(stock);
        }
        if(Objects.nonNull(price)&&productDetails.getProductPrice().equals(price)) {
            productDetails.setProductPrice(price);
        }
        if(Objects.nonNull(filters)) {
            log.warn("product filter tags has been changed");
            productDetails.setProductFilters(filters);
        }
        productService.save(productDetails);
        ra.addFlashAttribute("message","Product : "+productDetails.getProductName()+" is updated");
        return "redirect:/admin/products/list";

    }
    @PostMapping("/deleteProduct/{id}")
    public String deleteProduct(@PathVariable(name="id") Long ProductId,RedirectAttributes ra){
        productService.deleteProduct(ProductId);
        ra.addFlashAttribute("message","Product is deleted");
        return "redirect:/admin/products/list";
    }
    // Image upload directory method
    private String fileUploadDir(MultipartFile file) throws IOException {
        String rootPath = System.getProperty("user.dir");
        String uploadDir = rootPath + "/src/main/resources/static/uploads";

        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String fileName = UUID.randomUUID().toString() + "-" + file.getOriginalFilename();

        // Save the file to the upload directory
        String filePath = uploadDir + "/" + fileName;
        Path path = Paths.get(filePath);
        System.out.println(path);
        Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

        // Return the file path
        return fileName;
    }
}
