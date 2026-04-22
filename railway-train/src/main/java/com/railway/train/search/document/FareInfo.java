package com.railway.train.search.document;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FareInfo {

    private String coachType;
    private BigDecimal baseFare;
}
