package com.railway.train.search.document;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoachAvailability {

    private String coachType;
    private int totalSeats;
    private int availableSeats;
    private int racSeats;
    private int waitlistCount;
}
