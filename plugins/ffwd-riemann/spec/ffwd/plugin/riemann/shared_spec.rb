require 'ffwd/plugin/riemann/shared'

describe FFWD::Plugin::Riemann::Shared do
  class Foo
    include FFWD::Plugin::Riemann::Shared
  end

  let(:f){Foo.new}

  describe "#write_attributes" do
    it "should escape all attribute keys to strings" do
      ref = [::Riemann::Attribute.new(:key => "foo", :value => "bar")]
      e = double
      e.should_receive(:attributes=).with(ref)
      f.write_attributes e, {:foo => "bar"}
    end
  end

  describe "#make_event" do
    it "should escape all fields to strings iff they are symbols" do
      ref = ::Riemann::Event.new()
      ref.host = "foo"

      e = double(
        :attributes => {}, :tags => [], :time => nil, :state => nil,
        :description => nil, :ttl => nil, :key => nil, :value => nil)

      e.should_receive(:host).and_return(:foo)
      f.make_event(e).should eq(ref)
    end
  end
end
