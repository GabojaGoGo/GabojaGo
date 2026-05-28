package com.gabojago.tourism.data.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CongestionDeleteResponse {
    private long deletedBatchCount;
    private long deletedItemCount;
}
