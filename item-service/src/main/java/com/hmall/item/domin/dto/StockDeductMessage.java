package com.hmall.item.domin.dto;

import com.hmall.api.dto.OrderDetailDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockDeductMessage {
    private List<OrderDetailDTO> details;
}
