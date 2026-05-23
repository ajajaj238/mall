package com.hmall.user.controller;


import com.hmall.common.exception.BadRequestException;
import com.hmall.common.utils.BeanUtils;
import com.hmall.common.utils.CollUtils;
import com.hmall.common.utils.UserContext;
import com.hmall.user.domin.dto.AddressDTO;
import com.hmall.user.domin.po.Address;
import com.hmall.user.service.IAddressService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author zhj
 */
@RestController
@RequestMapping("/addresses")
@RequiredArgsConstructor
@Api(tags = "收货地址管理接口")
public class AddressController {

    private final IAddressService addressService;

    @ApiOperation("根据id查询地址")
    @GetMapping("{addressId}")
    public AddressDTO findAddressById(@ApiParam("地址id") @PathVariable("addressId") Long id) {
        // 1.根据id查询
        Address address = addressService.getById(id);
        // 2.判断当前用户
        Long userId = UserContext.getUser();
        if(!address.getUserId().equals(userId)){
            throw new BadRequestException("地址不属于当前登录用户");
        }
        return BeanUtils.copyBean(address, AddressDTO.class);
    }
    @ApiOperation("查询当前用户地址列表")
    @GetMapping
    public List<AddressDTO> findMyAddresses() {
        // 1.查询列表
        List<Address> list = addressService.query().eq("user_id", UserContext.getUser()).list();
        // 2.判空
        if (CollUtils.isEmpty(list)) {
            return CollUtils.emptyList();
        }
        // 3.转vo
        return BeanUtils.copyList(list, AddressDTO.class);
    }

    @ApiOperation("新增地址")
    @PostMapping
    public void addAddress(@RequestBody AddressDTO addressDTO) {
        // 1.获取当前用户
        Long userId = UserContext.getUser();
        // 2.转换为PO
        Address address = BeanUtils.copyBean(addressDTO, Address.class);
        address.setUserId(userId);
        // 3.如果设置为默认地址,先取消其他默认地址
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            addressService.update()
                    .set("is_default", 0)
                    .eq("user_id", userId)
                    .eq("is_default", 1)
                    .update();
        }
        // 4.保存地址
        addressService.save(address);
    }

    @ApiOperation("更新地址")
    @PutMapping("{addressId}")
    public void updateAddress(
            @ApiParam("地址id") @PathVariable("addressId") Long id,
            @RequestBody AddressDTO addressDTO) {
        // 1.根据id查询
        Address address = addressService.getById(id);
        if (address == null) {
            throw new BadRequestException("地址不存在");
        }
        // 2.判断当前用户
        Long userId = UserContext.getUser();
        if (!address.getUserId().equals(userId)) {
            throw new BadRequestException("地址不属于当前登录用户");
        }
        // 3.如果设置为默认地址,先取消其他默认地址
        if (addressDTO.getIsDefault() != null && addressDTO.getIsDefault() == 1) {
            addressService.update()
                    .set("is_default", 0)
                    .eq("user_id", userId)
                    .eq("is_default", 1)
                    .ne("id", id)
                    .update();
        }
        // 4.更新地址
        Address updateAddress = BeanUtils.copyBean(addressDTO, Address.class);
        updateAddress.setId(id);
        updateAddress.setUserId(userId);
        addressService.updateById(updateAddress);
    }

    @ApiOperation("删除地址")
    @DeleteMapping("{addressId}")
    public void deleteAddress(@ApiParam("地址id") @PathVariable("addressId") Long id) {
        // 1.根据id查询
        Address address = addressService.getById(id);
        if (address == null) {
            throw new BadRequestException("地址不存在");
        }
        // 2.判断当前用户
        Long userId = UserContext.getUser();
        if (!address.getUserId().equals(userId)) {
            throw new BadRequestException("地址不属于当前登录用户");
        }
        // 3.删除地址
        addressService.removeById(id);
    }
}
