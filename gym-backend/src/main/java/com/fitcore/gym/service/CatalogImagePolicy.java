package com.fitcore.gym.service;

import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fitcore.gym.exception.BadRequestException;

/** References only: never fetches URLs or uploads files. Unapproved legacy metadata stays inert. */
@Component
public class CatalogImagePolicy {
    private final Set<String> origins;
    public CatalogImagePolicy(@Value("${fitcore.catalog.image-origins:}") String configured){
        var approved=new HashSet<String>();
        for(String value:configured.split(","))if(!value.isBlank()){
            try{URI u=URI.create(value.trim());
                if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||!(u.getRawPath().isEmpty()||u.getRawPath().equals("/")))throw new IllegalArgumentException();
                approved.add(origin(u));
            }catch(IllegalArgumentException invalid){throw new IllegalStateException("Catalog images require explicit HTTPS origins");}
        }
        origins=Set.copyOf(approved);
    }
    private static String origin(URI u){return "https://"+u.getHost().toLowerCase(Locale.ROOT)+(u.getPort()==-1||u.getPort()==443?"":":"+u.getPort());}
    public boolean approved(String value){
        if(value==null||value.isBlank())return false;
        if(value.length()>255||value.contains("..")||value.contains("\\")||value.contains("%")||value.chars().anyMatch(Character::isISOControl))return false;
        if(value.matches("/images/[A-Za-z0-9_/-]+\\.(png|jpg|jpeg|webp|svg)"))return true;
        try{URI u=URI.create(value);return "https".equals(u.getScheme())&&u.getHost()!=null&&u.getUserInfo()==null&&u.getFragment()==null&&u.getQuery()==null&&origins.contains(origin(u));}
        catch(IllegalArgumentException invalid){return false;}
    }
    public String validateChange(String requested,String previous){
        String value=requested==null||requested.isBlank()?null:requested.trim();
        if(!Objects.equals(value,previous)&&value!=null&&!approved(value))throw new BadRequestException("Use a local /images/ reference or an approved HTTPS image origin");
        return value;
    }
}
