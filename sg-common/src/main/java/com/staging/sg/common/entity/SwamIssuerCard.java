package com.staging.sg.common.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Carte geree exclusivement par le switch/emetteur SWAM. */
@Entity
@Table(name = "issuer_swam_cards")
public class SwamIssuerCard extends AbstractSwamCard {
}
