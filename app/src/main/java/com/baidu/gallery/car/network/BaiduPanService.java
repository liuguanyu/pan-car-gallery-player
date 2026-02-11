package com.baidu.gallery.car.network;

import com.baidu.gallery.car.model.DeviceCodeResponse;
import com.baidu.gallery.car.model.FileInfo;
import com.baidu.gallery.car.model.FileListResponse;
import com.baidu.gallery.car.model.TokenResponse;
import com.baidu.gallery.car.model.UserInfoResponse;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

/**
 * 百度网盘API服务接口
 */
public interface BaiduPanService {
    
    /**
     * 获取设备码
     */
    @GET(ApiConstants.ENDPOINT_DEVICE_CODE)
    Call<DeviceCodeResponse> getDeviceCode(
            @Query("client_id") String clientId,
            @Query("scope") String scope,
            @Query("response_type") String responseType
    );
    
    /**
     * 轮询设备码状态获取token
     */
    @GET(ApiConstants.ENDPOINT_TOKEN)
    Call<TokenResponse> getTokenByDeviceCode(
            @Query("grant_type") String grantType,
            @Query("code") String deviceCode,
            @Query("client_id") String clientId,
            @Query("client_secret") String clientSecret
    );
    
    /**
     * 刷新token
     */
    @GET(ApiConstants.ENDPOINT_TOKEN)
    Call<TokenResponse> refreshToken(
            @Query("grant_type") String grantType,
            @Query("refresh_token") String refreshToken,
            @Query("client_id") String clientId,
            @Query("client_secret") String clientSecret
    );
    
    /**
     * 获取用户信息
     */
    @GET(ApiConstants.ENDPOINT_NAS)
    Call<UserInfoResponse> getUserInfo(
            @Query("method") String method,
            @Query("access_token") String accessToken
    );
    
    /**
     * 获取文件列表
     */
    @GET(ApiConstants.ENDPOINT_FILE)
    Call<FileListResponse> getFileList(
            @Query("method") String method,
            @Query("dir") String dir,
            @Query("order") String order,
            @Query("desc") int desc,
            @Query("start") int start,
            @Query("limit") int limit,
            @Query("web") int web,
            @Query("folder") int folder,
            @Query("access_token") String accessToken
    );
    
    /**
     * 递归获取文件列表
     */
    @GET(ApiConstants.ENDPOINT_MULTIMEDIA)
    Call<FileListResponse> getFileListRecursive(
            @Query("method") String method,
            @Query("path") String path,
            @Query("order") String order,
            @Query("desc") int desc,
            @Query("limit") int limit,
            @Query("recursion") int recursion,
            @Query("access_token") String accessToken
    );
    
    /**
     * 获取文件信息（包括下载链接）
     */
    @GET(ApiConstants.ENDPOINT_MULTIMEDIA)
    Call<FileListResponse> getFileInfo(
            @Query("method") String method,
            @Query("fsids") String fsids,
            @Query("dlink") int dlink,
            @Query("thumb") int thumb,
            @Query("access_token") String accessToken
    );
}