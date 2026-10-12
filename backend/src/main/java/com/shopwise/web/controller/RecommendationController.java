package com.shopwise.web.controller;

import com.shopwise.recommendation.InteractionRecorder;
import com.shopwise.recommendation.RecommendationService;
import com.shopwise.security.ActorResolver;
import com.shopwise.security.ActorResolver.ActorContext;
import com.shopwise.service.CatalogService;
import com.shopwise.web.dto.RecommendationDtos.EventAck;
import com.shopwise.web.dto.RecommendationDtos.EventBatch;
import com.shopwise.web.dto.RecommendationDtos.PlacementPerformance;
import com.shopwise.web.dto.RecommendationDtos.RecommendationModelStatus;
import com.shopwise.web.dto.RecommendationDtos.RecommendationRail;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Recommendation rails and their measurement.
 *
 * <p>Every rail returns an empty item list rather than an error when it has nothing to say. A
 * storefront slot that fails loudly is worse than one that quietly does not appear, and the caller
 * can tell "nothing yet" from "something broke" by reading {@code fallback}.</p>
 */
@RestController
@RequestMapping("/api/v1/recommendations")
@Tag(name = "Recommendations", description = "Personalised, similar and complementary product rails")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final CatalogService catalogService;
    private final InteractionRecorder recorder;
    private final ActorResolver actorResolver;

    public RecommendationController(RecommendationService recommendationService,
                                    CatalogService catalogService,
                                    InteractionRecorder recorder,
                                    ActorResolver actorResolver) {
        this.recommendationService = recommendationService;
        this.catalogService = catalogService;
        this.recorder = recorder;
        this.actorResolver = actorResolver;
    }

    @GetMapping("/for-you")
    @Operation(summary = "Personalised rail for the current shopper, guests included")
    public RecommendationRail forYou(@RequestParam(required = false) Integer limit,
                                     HttpServletRequest request) {
        return recommendationService.forActor(actorResolver.resolve(request), limit);
    }

    @GetMapping("/trending")
    @Operation(summary = "Time-decayed popular products")
    public RecommendationRail trending(@RequestParam(required = false) Integer limit) {
        return recommendationService.trending(limit);
    }

    @GetMapping("/cart")
    @Operation(summary = "Products that go with what is already in the basket")
    public RecommendationRail cart(@RequestParam(required = false) Integer limit,
                                   HttpServletRequest request) {
        return recommendationService.cartCrossSell(actorResolver.resolve(request), limit);
    }

    @GetMapping("/products/{slugOrSku}/similar")
    @Operation(summary = "Products similar to the given one")
    public RecommendationRail similar(@PathVariable String slugOrSku,
                                      @RequestParam(required = false) Integer limit) {
        return recommendationService.similar(productId(slugOrSku), limit);
    }

    @GetMapping("/products/{slugOrSku}/also-viewed")
    @Operation(summary = "Products browsed alongside the given one")
    public RecommendationRail alsoViewed(@PathVariable String slugOrSku,
                                         @RequestParam(required = false) Integer limit) {
        return recommendationService.alsoViewed(productId(slugOrSku), limit);
    }

    @GetMapping("/products/{slugOrSku}/also-bought")
    @Operation(summary = "Products bought together with the given one")
    public RecommendationRail alsoBought(@PathVariable String slugOrSku,
                                         @RequestParam(required = false) Integer limit) {
        return recommendationService.alsoBought(productId(slugOrSku), limit);
    }

    /**
     * Records what the browser saw and clicked.
     *
     * <p>Only measurement events are accepted. Behavioural events are written by the services that
     * handle them, because a client that could report a purchase could also invent one.</p>
     */
    @PostMapping("/events")
    @Operation(summary = "Record impression and click events for a rendered rail")
    public EventAck events(@Valid @RequestBody EventBatch batch, HttpServletRequest request) {
        ActorContext actor = actorResolver.resolve(request);
        List<InteractionRecorder.ClientEvent> events = batch.events().stream()
                .map(event -> new InteractionRecorder.ClientEvent(
                        event.type(), event.productId(), event.recommendationId(), event.surface()))
                .toList();
        InteractionRecorder.Result result = recorder.recordClientEvents(actor, events);
        return new EventAck(result.recorded(), result.rejected());
    }

    @GetMapping("/status")
    @Operation(summary = "Model health. Requires SUPPORT or ADMIN.")
    public RecommendationModelStatus status() {
        return recommendationService.status();
    }

    @GetMapping("/performance")
    @Operation(summary = "Click-through per placement. Requires SUPPORT or ADMIN.")
    public List<PlacementPerformance> performance(
            @Parameter(description = "Rolling window in days")
            @RequestParam(defaultValue = "14") int days) {
        return recommendationService.placementPerformance(days);
    }

    private Long productId(String slugOrSku) {
        return catalogService.detail(slugOrSku).id();
    }
}
