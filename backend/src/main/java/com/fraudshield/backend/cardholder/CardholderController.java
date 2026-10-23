package com.fraudshield.backend.cardholder;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fraudshield.backend.authorization.SemanticValidationException;
import com.fraudshield.backend.workflow.CurrentActor;
import com.fraudshield.backend.workflow.StepUpVerificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/me")
public class CardholderController {
    private final CardholderService service; private final CurrentActor actors; private final StepUpVerificationService stepUps;
    public CardholderController(CardholderService service,CurrentActor actors,StepUpVerificationService stepUps){this.service=service;this.actors=actors;this.stepUps=stepUps;}
    @GetMapping("/cards") public Object cards(Authentication a,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){validate(page,size);return service.cards(actors.require(a).getId(),page,size);}
    @GetMapping("/transactions") public Object transactions(Authentication a,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){validate(page,size);return service.transactions(actors.require(a).getId(),page,size);}
    @GetMapping("/transactions/{txnId}") public Object transaction(Authentication a,@PathVariable String txnId){return service.transaction(actors.require(a).getId(),txnId);}
    @GetMapping("/step-ups") public Object stepUps(Authentication a,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){validate(page,size);return service.stepUps(actors.require(a).getId(),page,size);}
    @PostMapping("/step-ups/{id}/responses") public Object respond(Authentication a,@PathVariable UUID id,@Valid @RequestBody StepUpResponse request){return stepUps.respond(id,actors.require(a).getId(),request.response(),request.otp());}
    @PostMapping("/cards/{cardId}/block") public Object block(Authentication a,@PathVariable String cardId){return service.block(actors.require(a).getId(),cardId);}
    private void validate(int page,int size){if(page<0)throw new SemanticValidationException("page","Must be zero or greater");if(size<1||size>100)throw new SemanticValidationException("size","Must be between 1 and 100");}
    public record StepUpResponse(@NotBlank String response,@JsonProperty("otp") String otp){}
}
