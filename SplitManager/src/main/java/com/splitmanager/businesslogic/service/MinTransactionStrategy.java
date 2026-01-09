// language: java
package com.splitmanager.service.strategy;

import com.splitmanager.domain.accounting.Settlement;
import com.splitmanager.domain.registry.Membership;

import java.math.BigDecimal;
import java.util.*;

/**
 * Algoritmo greedy per minimizzare il numero di transazioni necessarie
 * a saldare tutti i debiti in un gruppo.
 *
 * ALGORITMO:
 * 1. Separare i membri in debitori (saldo negativo) e creditori (saldo positivo)
 * 2. Ordinare i debitori per importo del debito (decrescente)
 * 3. Ordinare i creditori per importo del credito (decrescente)
 * 4. Abbinare il debitore più grande con il creditore più grande
 * 5. Creare uno settlement per min(debito, credito)
 * 6. Ripetere finché tutti i saldi non sono zero
 *
 * COMPLESSITÀ: O(n log n) dovuta all'ordinamento
 *
 * ESEMPIO:
 * Input:
 * - Alice: -50€ (deve 50)
 * - Bob: -30€ (deve 30)
 * - Charlie: +80€ (credito 80)
 *
 * Output ottimizzato:
 * - Alice paga 50€ a Charlie
 * - Bob paga 30€ a Charlie
 * Totale: 2 transazioni
 */
public class MinTransactionsStrategy implements BalanceStrategy {

    @Override
    public List<Settlement> optimize(Map<Membership, BigDecimal> balances) {
        List<Settlement> settlements = new ArrayList<>();

        // Separa debitori e creditori
        List<DebtorCredit> debtors = new ArrayList<>();
        List<DebtorCredit> creditors = new ArrayList<>();

        for (Map.Entry<Membership, BigDecimal> entry : balances.entrySet()) {
            BigDecimal amount = entry.getValue();

            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                // Debitore: memorizza il valore assoluto
                debtors.add(new DebtorCredit(entry.getKey(), amount.abs()));
            } else if (amount.compareTo(BigDecimal.ZERO) > 0) {
                // Creditore
                creditors.add(new DebtorCredit(entry.getKey(), amount));
            }
            // Ignora se il saldo è zero
        }

        // Ordina per importo decrescente (i più grandi prima)
        debtors.sort((a, b) -> b.amount.compareTo(a.amount));
        creditors.sort((a, b) -> b.amount.compareTo(a.amount));

        // Matching greedy
        int i = 0; // indice per i debitori
        int j = 0; // indice per i creditori

        while (i < debtors.size() && j < creditors.size()) {
            DebtorCredit debtor = debtors.get(i);
            DebtorCredit creditor = creditors.get(j);

            // Importo da saldare: minimo tra debito e credito
            BigDecimal settleAmount = debtor.amount.min(creditor.amount);

            // Crea settlement (non persistito, solo suggerito)
            Settlement settlement = new Settlement(
                    debtor.membership.getGroup(),
                    debtor.membership,  // pagatore
                    creditor.membership, // ricevente
                    settleAmount
            );

            settlements.add(settlement);

            // Aggiorna importi residui
            debtor.amount = debtor.amount.subtract(settleAmount);
            creditor.amount = creditor.amount.subtract(settleAmount);

            // Passa al successivo se completamente saldato
            if (debtor.amount.compareTo(BigDecimal.ZERO) == 0) {
                i++;
            }
            if (creditor.amount.compareTo(BigDecimal.ZERO) == 0) {
                j++;
            }
        }

        return settlements;
    }

    /**
     * Classe helper per contenere la membership e l'importo.
     * Mutabile per permettere l'aggiornamento degli importi durante il matching greedy.
     */
    private static class DebtorCredit {
        Membership membership;
        BigDecimal amount;

        DebtorCredit(Membership membership, BigDecimal amount) {
            this.membership = membership;
            this.amount = amount;
        }
    }
}
