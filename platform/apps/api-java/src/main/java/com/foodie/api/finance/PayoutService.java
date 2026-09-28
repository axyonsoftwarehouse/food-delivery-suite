package com.foodie.api.finance;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Repasses atuais do entregador e conclusão dos pedidos históricos da loja. */
@Service
public class PayoutService {
    private static final Set<String> ACTIVE = Set.of("courier");
    private static final Set<String> OPEN = Set.of("requested", "approved");

    private final PayoutRepository payouts;
    private final LedgerRepository ledger;
    private final LedgerService ledgerService;

    public PayoutService(PayoutRepository payouts, LedgerRepository ledger, LedgerService ledgerService) {
        this.payouts = payouts;
        this.ledger = ledger;
        this.ledgerService = ledgerService;
    }

    private static Party requestParty(User user) {
        Party party = Party.of(user);
        if (party == null || !ACTIVE.contains(party.party())) throw new ApiException(403, "A carteira de repasses está disponível apenas para entregadores");
        return party;
    }

    public List<PayoutRepository.Method> methods(User user) {
        Party party = requestParty(user);
        return payouts.methods(party.party(), party.id());
    }

    public PayoutRepository.Method createMethod(User user, String type, String details) {
        Party party = requestParty(user);
        String cleanType = type == null ? "" : type.strip().toLowerCase(java.util.Locale.ROOT);
        if (cleanType.length() < 2 || cleanType.length() > 30) throw new ApiException(400, "Tipo de saque inválido");
        String cleanDetails = details == null ? "" : details.strip();
        if (cleanDetails.length() > 500) throw new ApiException(400, "Detalhes muito longos");
        long id = payouts.createMethod(party.party(), party.id(), cleanType, cleanDetails);
        return new PayoutRepository.Method(id, party.party(), party.id(), cleanType, cleanDetails, true);
    }

    public void deleteMethod(User user, long id) {
        Party party = requestParty(user);
        if (payouts.deleteMethod(party.party(), party.id(), id) == 0) throw new ApiException(404, "Método não encontrado");
    }

    public long available(Party party) {
        return ledgerService.balance(party) - payouts.reserved(party.party(), party.id());
    }

    public Map<String, Object> wallet(User user) {
        Party party = requestParty(user);
        long balance = ledgerService.balance(party);
        long reserved = payouts.reserved(party.party(), party.id());
        return Map.of(
            "party", party.party(),
            "partyId", party.id(),
            "balanceCents", balance,
            "reservedCents", reserved,
            "availableCents", balance - reserved);
    }

    @Transactional
    public Map<String, Object> createRequest(User user, long amountCents, Long methodId, String note) {
        Party party = requestParty(user);
        if (amountCents <= 0) throw new ApiException(400, "Informe um valor válido");
        if (methodId != null) {
            PayoutRepository.Method method = payouts.findMethod(methodId).orElseThrow(() -> new ApiException(400, "Método de saque não encontrado"));
            if (!method.party().equals(party.party()) || method.partyId() != party.id()) throw new ApiException(403, "Método de saque de outra conta");
        }
        long available = available(party);
        if (amountCents > available) throw new ApiException(409, "Saldo disponível insuficiente");
        String cleanNote = note == null ? "" : note.strip();
        if (cleanNote.length() > 255) throw new ApiException(400, "Observação muito longa");
        long id = payouts.createRequest(party.party(), party.id(), amountCents, methodId, cleanNote);
        return Map.of("id", id, "amountCents", amountCents, "status", "requested");
    }

    public List<Map<String, Object>> requests(User user) {
        Party party = requestParty(user);
        return payouts.requests(party.party(), party.id());
    }

    public List<Map<String, Object>> adminRequests(String status) {
        String filter = status == null || status.isBlank() ? null : status.strip();
        if (filter != null && !Set.of("requested", "approved", "paid", "rejected").contains(filter)) {
            throw new ApiException(400, "Filtro de status inválido");
        }
        return payouts.adminRequests(filter);
    }

    @Transactional
    public Map<String, Object> decide(User actor, long id, String decision, String note) {
        Map<String, Object> request = payouts.findRequest(id).orElseThrow(() -> new ApiException(404, "Solicitação não encontrada"));
        String status = String.valueOf(request.get("status"));
        if (!OPEN.contains(status)) throw new ApiException(409, "Solicitação já finalizada");
        String next = switch (decision == null ? "" : decision.strip()) {
            case "approve" -> "approved";
            case "reject" -> "rejected";
            case "paid" -> "paid";
            default -> throw new ApiException(400, "Decisão inválida");
        };
        String cleanNote = note == null ? "" : note.strip();
        if (payouts.updateStatus(id, next, actor.id(), cleanNote) == 0) throw new ApiException(409, "Solicitação já finalizada");
        if ("paid".equals(next)) {
            String party = String.valueOf(request.get("party"));
            long partyId = ((Number) request.get("party_id")).longValue();
            long amount = ((Number) request.get("amount_cents")).longValue();
            ledger.insert(party, partyId, null, "payout", -amount, "Repasse #" + id);
        }
        return Map.of("id", id, "status", next);
    }
}
