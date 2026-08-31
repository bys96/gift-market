package com.giftmarket.admin.dto.response;
import org.springframework.data.domain.Page;
import java.util.List;
public record AdminCancellationPageResponse(List<AdminCancellationSummaryResponse> content,int page,int size,long totalElements,int totalPages,boolean first,boolean last){
 public static AdminCancellationPageResponse from(Page<?> p,List<AdminCancellationSummaryResponse> c){return new AdminCancellationPageResponse(c,p.getNumber(),p.getSize(),p.getTotalElements(),p.getTotalPages(),p.isFirst(),p.isLast());}
}
