package com.rishavdev.UrlShortener.web.controller;

import com.rishavdev.UrlShortener.ApplicationProperties;
import com.rishavdev.UrlShortener.domain.exceptions.ShortUrlNotFoundException;
import com.rishavdev.UrlShortener.domain.models.CreateShortUrlCmd;
import com.rishavdev.UrlShortener.domain.models.PagedResult;
import com.rishavdev.UrlShortener.domain.models.ShortUrlDto;
import com.rishavdev.UrlShortener.domain.services.ShortUrlService;
import com.rishavdev.UrlShortener.web.dtos.CreateShortUrlForm;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
public class HomeController {
    private final ShortUrlService shortUrlService;
    private final ApplicationProperties properties;
    private final SecurityUtils securityUtils;

    public HomeController(ShortUrlService shortUrlService, ApplicationProperties properties, SecurityUtils securityUtils) {
        this.shortUrlService = shortUrlService;
        this.properties = properties;
        this.securityUtils = securityUtils;
    }

    private void addShortUrlsDataToModel(Model model, int pageNo) {
        PagedResult<ShortUrlDto> shortUrls = shortUrlService.findAllPublicShortUrls(pageNo, properties.pageSize());
        model.addAttribute("shortUrls", shortUrls);
        model.addAttribute("baseUrl", properties.baseUrl());
    }

    @GetMapping("/")
    public String home(
            @RequestParam(defaultValue = "1") Integer page,
            Model model) {
        this.addShortUrlsDataToModel(model, page);
        model.addAttribute("paginationUrl", "/");
        model.addAttribute("createShortUrlForm", new CreateShortUrlForm("", false, null));
        return "index";
    }

    @PostMapping("/short-urls")
    String createShortUrls(@ModelAttribute("createShortUrlForm") @Valid CreateShortUrlForm form,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes,
                           Model model) {
        if (bindingResult.hasErrors()) {
            this.addShortUrlsDataToModel(model, 1);
            return "index";
        }

        try {
            CreateShortUrlCmd cmd = new CreateShortUrlCmd(form.originalUrl(), form.isPrivate(), form.expirationInDays(), securityUtils.getCurrentUserId());
            ShortUrlDto shortUrlDto = shortUrlService.createShortUrl(cmd);
            redirectAttributes.addFlashAttribute("successMessage", "Short Url created successfully" + properties.baseUrl() + shortUrlDto.shortKey());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed");
        }

        return "redirect:/";
    }

    @GetMapping("/s/{shortKey}")
    String redirectToOriginalUrl(@PathVariable String shortKey) {
        Long userId = securityUtils.getCurrentUserId();
        Optional<ShortUrlDto> optionalShortUrlDto = shortUrlService.accessShortUrl(shortKey, userId);
        if (optionalShortUrlDto.isEmpty()) {
            throw new ShortUrlNotFoundException("Invalid Short Key: " + shortKey);
        }
        ShortUrlDto shortUrlDto = optionalShortUrlDto.get();
        return "redirect:" + shortUrlDto.originalUrl();
    }

    @GetMapping("/login")
    String loginForm() {
        return "login";
    }

    @PostMapping("/delete-urls")
    @PreAuthorize("hasAnyRole('USER', 'MODERATOR')")
    public String deleteUrls(@RequestParam(value = "ids", required = false) List<Long> ids, RedirectAttributes redirectAttributes) {
        if (ids == null || ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No URLs selected for deletion");
            return "redirect:/my-urls";
        }

        try {
            Long currentUserId = securityUtils.getCurrentUserId();
            shortUrlService.deleteUserShortUrls(ids, currentUserId);
            redirectAttributes.addFlashAttribute("successMessage", "Selected URLs are deleted successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting URLs: " + e.getMessage());
        }

        return "redirect:/my-urls";
    }

    @GetMapping("/my-urls")
    public String showUserUrls(@RequestParam(defaultValue = "1") int page,
                               Model model) {
        var currentUserId = securityUtils.getCurrentUserId();
        PagedResult<ShortUrlDto> userUrls = shortUrlService.getUserShortUrls(currentUserId, page, properties.pageSize());
        model.addAttribute("shortUrls", userUrls);
        model.addAttribute("baseUrl", properties.baseUrl());
        model.addAttribute("paginationUrl", "/my-urls");
        return "my-urls";
    }
}

