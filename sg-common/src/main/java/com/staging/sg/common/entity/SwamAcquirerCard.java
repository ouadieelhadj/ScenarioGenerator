package com.staging.sg.common.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Carte geree exclusivement par le membre/acquereur SWAM. */
@Entity
@Table(name = "acquirer_swam_cards")
public class SwamAcquirerCard extends AbstractSwamCard {
}
