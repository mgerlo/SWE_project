// language: java
package com.splitmanager.service.strategy;

import com.splitmanager.domain.accounting.Settlement;
import com.splitmanager.domain.registry.Membership;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Interfaccia Strategy per gli algoritmi di ottimizzazione dei saldi.
 *
 * Definisce il contratto per diversi algoritmi che ottimizzano
 * la riconciliazione dei debiti all'interno di un gruppo.
 *
 * Diverse implementazioni possono fornire criteri di ottimizzazione differenti:
 * - Minimizzare il numero di transazioni (MinTransactionsStrategy)
 * - Minimizzare l'importo massimo per transazione
 * - Bilanciare gli importi delle transazioni in modo uniforme
 * - ecc.
 */
public interface BalanceStrategy {

    /**
     * Ottimizza la riconciliazione dei debiti.
     *
     * Riceve una mappa dei saldi correnti e restituisce una lista ottimizzata
     * di oggetti Settlement che, se eseguiti, andranno a bilanciare tutti i conti.
     *
     * @param balances Mappa da Membership al loro saldo netto corrente
     *                 (positivo = credito, negativo = debito)
     * @return Lista di oggetti Settlement ottimizzati (in stato PENDING, non persistiti)
     */
    List<Settlement> optimize(Map<Membership, BigDecimal> balances);
}
