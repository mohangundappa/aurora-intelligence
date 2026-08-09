package com.aurora.identity;

import com.aurora.common.CdpProfile;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/identity")
public class IdentityController {
  private final IdentityStitcher stitcher;

  public IdentityController(IdentityStitcher stitcher) {
    this.stitcher = stitcher;
  }

  @GetMapping("/{anonymousId}/timeline")
  public List<CdpProfile.IdentityLink> timeline(@PathVariable String anonymousId) {
    return stitcher.timeline(anonymousId);
  }
}
