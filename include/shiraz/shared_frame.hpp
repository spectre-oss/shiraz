// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <cstdint>
#include <memory>
#include <vector>
namespace shiraz {
// Descriptor-owned CPU transport for native windows across app processes.
// Mapping size is sealed; geometry and frame publication are synchronized.
class SharedFrame {
 public:
  static std::unique_ptr<SharedFrame> create();
  static std::unique_ptr<SharedFrame> import(int descriptor);
  ~SharedFrame();
  SharedFrame(const SharedFrame&)=delete;
  SharedFrame& operator=(const SharedFrame&)=delete;
  int duplicate() const;
  uint64_t post(int width,int height,const uint32_t* pixels,uint64_t count,bool allowResize=true);
  bool read(int& width,int& height,uint64_t& revision,std::vector<uint32_t>& pixels,bool onlyIfChanged=false);
 private:
  SharedFrame(int descriptor,void* mapping):descriptor_(descriptor),mapping_(mapping){}
  int descriptor_;
  void* mapping_;
};
}
